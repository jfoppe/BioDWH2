package de.unibi.agbi.biodwh2.core.io.mvstore;

import org.h2.mvstore.MVMap;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class MVStoreIndex {
    private static class PageMetadata {
        public Long minId;
        public Long maxId;
        public int slotsUsed;
    }

    private static final int PAGE_SIZE = 1000;

    private final String name;
    private final String key;
    private final boolean arrayIndex;
    private final MVMap<Comparable<?>, ConcurrentLinkedQueue<Long>> map;
    private final MVMap<Long, ConcurrentLinkedQueue<Long>> pagesMap;
    private final Map<Long, PageMetadata> pagesMetadataMap;
    private long nextPageIndex;

    MVStoreIndex(final MVStoreDB db, final String name, final String key, final boolean arrayIndex) {
        this.name = name;
        this.key = key;
        this.arrayIndex = arrayIndex;
        map = db.openMap(name);
        pagesMap = db.openMap(name + "!pages");
        pagesMetadataMap = new HashMap<>();
        nextPageIndex = 0;
        for (final Long pageIndex : pagesMap.keySet()) {
            nextPageIndex = pageIndex + 1;
            final PageMetadata metadata = new PageMetadata();
            final ConcurrentLinkedQueue<Long> page = pagesMap.get(pageIndex);
            metadata.slotsUsed = 0;
            for (Long slot : page) {
                if (slot == null)
                    break;
                metadata.slotsUsed++;
                metadata.maxId = slot;
            }
            if (metadata.slotsUsed > 0)
                metadata.minId = page.peek();
            pagesMetadataMap.put(pageIndex, metadata);
        }
        sortAllPages();
    }

    public String getName() {
        return name;
    }

    void update(final MVStoreModel obj) {
        if (arrayIndex)
            update((Comparable<?>[]) obj.get(key), obj.getId().getIdValue());
        else
            update((Comparable<?>) obj.get(key), obj.getId().getIdValue());
    }

    private void update(final Comparable<?> indexKey, final long id) {
        if (indexKey != null)
            insertToPage(indexKey, id);
    }

    private synchronized void insertToPage(final Comparable<?> indexKey, final long id) {
        ConcurrentLinkedQueue<Long> pages = map.get(indexKey);
        boolean pagesChanged = false;
        if (pages == null) {
            pages = new ConcurrentLinkedQueue<>();
            pagesChanged = true;
        }
        Long matchedPage = null;
        for (Long pageIndex : pages) {
            final PageMetadata metadata = pagesMetadataMap.get(pageIndex);
            if (metadata.minId == null || metadata.minId > id)
                continue;
            if (metadata.maxId > id || metadata.slotsUsed < PAGE_SIZE) {
                matchedPage = pageIndex;
                break;
            }
        }
        if (matchedPage == null) {
            final ConcurrentLinkedQueue<Long> page = new ConcurrentLinkedQueue<>();
            page.add(id);
            final PageMetadata metadata = new PageMetadata();
            metadata.slotsUsed = 1;
            metadata.minId = metadata.maxId = id;
            pages.add(nextPageIndex);
            pagesMap.put(nextPageIndex, page);
            pagesMetadataMap.put(nextPageIndex, metadata);
            nextPageIndex++;
            pagesChanged = true;
        } else {
            final PageMetadata metadata = pagesMetadataMap.get(matchedPage);
            final ConcurrentLinkedQueue<Long> page = pagesMap.get(matchedPage);
            if (!page.contains(id)) {
                page.add(id);
                metadata.slotsUsed++;
                if (metadata.maxId < id)
                    metadata.maxId = id;
                pagesMap.put(matchedPage, page);
            }
        }
        if (pagesChanged)
            map.put(indexKey, pages);
    }

    private void update(final Comparable<?>[] indexKeys, final long id) {
        for (final Comparable<?> indexKey : indexKeys)
            if (indexKey != null)
                insertToPage(indexKey, id);
    }

    public Set<Long> find(final Comparable<?> indexKey) {
        final ConcurrentLinkedQueue<Long> pages = map.get(indexKey);
        if (pages == null)
            return new HashSet<>();
        Set<Long> idSet = new HashSet<>();
        for (final Long pageIndex : pages)
            idSet.addAll(pagesMap.get(pageIndex));
        return idSet;
    }

    public String getKey() {
        return key;
    }

    public boolean isArrayIndex() {
        return arrayIndex;
    }

    void remove(final MVStoreModel obj) {
        if (arrayIndex)
            remove((Comparable<?>[]) obj.get(key), obj.getId().getIdValue());
        else
            remove((Comparable<?>) obj.get(key), obj.getId().getIdValue());
    }

    private void remove(final Comparable<?> indexKey, final long id) {
        if (indexKey != null)
            removeFromPage(indexKey, id);
    }

    private synchronized void removeFromPage(final Comparable<?> indexKey, final long id) {
        final ConcurrentLinkedQueue<Long> pages = map.get(indexKey);
        if (pages == null)
            return;
        Long pageIndexToRemove = null;
        for (Long pageIndex : pages) {
            final PageMetadata metadata = pagesMetadataMap.get(pageIndex);
            if (metadata.minId == null || metadata.minId > id || metadata.maxId < id)
                continue;
            final ConcurrentLinkedQueue<Long> page = pagesMap.get(pageIndex);
            page.remove(id);
            metadata.slotsUsed--;
            if (metadata.slotsUsed == 0) {
                pageIndexToRemove = pageIndex;
            } else {
                if (metadata.maxId == id)
                    metadata.maxId = page.stream().max(Long::compareTo).get();
                if (metadata.minId == id)
                    metadata.minId = page.peek();
                pagesMap.put(pageIndex, page);
            }
            break;
        }
        if (pageIndexToRemove != null) {
            pagesMap.remove(pageIndexToRemove);
            pages.remove(pageIndexToRemove);
            pagesMetadataMap.remove(pageIndexToRemove);
            map.put(indexKey, pages);
        }
    }

    private void remove(final Comparable<?>[] indexKeys, final long id) {
        for (final Comparable<?> indexKey : indexKeys)
            if (indexKey != null)
                removeFromPage(indexKey, id);
    }

    private void sortAllPages() {
        for (final Comparable<?> key : map.keySet())
            sortKeyPages(key);
    }

    private void sortKeyPages(final Comparable<?> key) {
        final ConcurrentLinkedQueue<Long> pageIndicesQueue = map.get(key);
        if (pageIndicesQueue == null || pageIndicesQueue.size() == 0)
            return;
        final Long[] pageIndices = pageIndicesQueue.stream().sorted().toArray(Long[]::new);
        final Set<Long> ids = new HashSet<>();
        for (final Long pageIndex : pageIndices)
            ids.addAll(pagesMap.get(pageIndex));
        Long[] sortedIds = ids.stream().sorted().toArray(Long[]::new);
        int nextPageIndex = 0;
        for (int i = 0; i < sortedIds.length; i += PAGE_SIZE) {
            final Long pageIndex = pageIndices[nextPageIndex];
            final Long[] page = new Long[Math.min(PAGE_SIZE, sortedIds.length - i)];
            System.arraycopy(sortedIds, i, page, 0, page.length);
            pagesMap.put(pageIndex, new ConcurrentLinkedQueue<>(Arrays.asList(page)));
            final PageMetadata metadata = pagesMetadataMap.get(pageIndex);
            metadata.minId = page[0];
            metadata.maxId = page[page.length - 1];
            metadata.slotsUsed = page.length;
            pagesMetadataMap.put(pageIndex, metadata);
            nextPageIndex++;
        }
        if (nextPageIndex < pageIndices.length) {
            final Long[] newPageIndices = new Long[nextPageIndex];
            System.arraycopy(pageIndices, 0, newPageIndices, 0, newPageIndices.length);
            map.put(key, new ConcurrentLinkedQueue<>(Arrays.asList(newPageIndices)));
        }
    }
}
