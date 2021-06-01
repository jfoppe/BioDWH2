package de.unibi.agbi.biodwh2.core.etl;

import de.unibi.agbi.biodwh2.core.DataSource;
import de.unibi.agbi.biodwh2.core.Workspace;
import de.unibi.agbi.biodwh2.core.exceptions.ExporterException;
import de.unibi.agbi.biodwh2.core.exceptions.ExporterFormatException;
import de.unibi.agbi.biodwh2.core.io.obo.*;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.Node;
import de.unibi.agbi.biodwh2.core.model.graph.NodeBuilder;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class OntologyGraphExporter<D extends DataSource> extends GraphExporter<D> {
    private static class EdgeCacheEntry {
        public long sourceNodeId;
        public String propertyKey;
        public String propertyValue;
    }

    private static final String ID_PROPERTY = "id";

    public OntologyGraphExporter(final D dataSource) {
        super(dataSource);
    }

    @Override
    protected boolean exportGraph(final Workspace workspace, final Graph graph) throws ExporterException {
        final boolean ignoreObsolete = ignoreObsolete(workspace);
        graph.setNodeIndexPropertyKeys(ID_PROPERTY);
        try {
            final OboReader reader = new OboReader(dataSource.resolveSourceFilePath(workspace, getOntologyFileName()),
                                                   StandardCharsets.UTF_8);
            exportHeader(graph, reader.getHeader());
            exportEntries(ignoreObsolete, graph, reader);
        } catch (IOException e) {
            throw new ExporterFormatException("Failed to export '" + getOntologyFileName() + "'", e);
        }
        return true;
    }

    private void exportHeader(final Graph graph, final OboHeader header) {
        final NodeBuilder builder = graph.buildNode().withLabel("Header");
        builder.withPropertyIfNotNull("format_version", header.getFormatVersion());
        builder.withPropertyIfNotNull("data_version", header.getDataVersions());
        builder.withPropertyIfNotNull("date", header.getDate());
        builder.withPropertyIfNotNull("saved_by", header.getSavedBy());
        builder.withPropertyIfNotNull("auto_generated_by", header.getAutoGeneratedBy());
        builder.withPropertyIfNotNull("default_namespace", header.getDefaultNamespace());
        builder.withPropertyIfNotNull("remarks", header.getRemarks());
        builder.withPropertyIfNotNull("ontology", header.getOntology());
        builder.withPropertyIfNotNull("imports", header.getImports());
        builder.withPropertyIfNotNull("owl_axioms", header.getOWLAxioms());
        builder.withPropertyIfNotNull("treat_xrefs_as_equivalent", header.treatXrefsAsEquivalent());
        builder.withPropertyIfNotNull("treat_xrefs_as_genus_differentia", header.treatXrefsAsGenusDifferentia());
        builder.withPropertyIfNotNull("treat_xrefs_as_has_subclass", header.treatXrefsAsHasSubclass());
        builder.withPropertyIfNotNull("treat_xrefs_as_is_a", header.treatXrefsAsIsA());
        builder.withPropertyIfNotNull("treat_xrefs_as_relationship", header.treatXrefsAsRelationship());
        builder.withPropertyIfNotNull("treat_xrefs_as_reverse_genus_differentia",
                                      header.treatXrefsAsReverseGenusDifferentia());
        builder.withPropertyIfNotNull("property_values", header.getPropertyValues());
        for (final String unreservedTokenKey : header.getUnreservedTokenKeys()) {
            final String key = StringUtils.replace(unreservedTokenKey, "-", "_");
            builder.withPropertyIfNotNull(key, header.get(key));
        }
        final Node node = builder.build();
        exportHeaderSubsetDefinitions(graph, header, node);
        exportHeaderSynonymTypeDefinitions(graph, header, node);
        exportHeaderIdspaces(graph, header, node);

    }

    private void exportHeaderSubsetDefinitions(final Graph graph, final OboHeader header, final Node headerNode) {
        final String[] subsetDefs = header.getSubsetDefs();
        if (subsetDefs != null)
            for (final String subsetDef : subsetDefs)
                exportHeaderSubsetDefinition(graph, headerNode, subsetDef);
    }

    private void exportHeaderSubsetDefinition(final Graph graph, final Node headerNode, final String subsetDef) {
        final String[] parts = StringUtils.split(subsetDef, " ", 2);
        final String name = StringUtils.strip(parts[1].trim(), "\"");
        final Node subsetDefNode = graph.addNode("Subset", ID_PROPERTY, parts[0], "name", name);
        graph.addEdge(headerNode, subsetDefNode, "HAS_SUBSET");
    }

    private void exportHeaderSynonymTypeDefinitions(final Graph graph, final OboHeader header, final Node headerNode) {
        final String[] synonymTypeDefs = header.getSynonymTypeDefs();
        if (synonymTypeDefs != null)
            for (final String synonymTypeDef : synonymTypeDefs)
                exportHeaderSynonymTypeDefinition(graph, headerNode, synonymTypeDef);
    }

    private void exportHeaderSynonymTypeDefinition(final Graph graph, final Node headerNode,
                                                   final String synonymTypeDef) {
        final int idSplitIndex = synonymTypeDef.indexOf(' ');
        final String id = synonymTypeDef.substring(0, idSplitIndex);
        final boolean endsWithOptionalSynonymScope = Arrays.stream(SynonymScope.values()).anyMatch(
                scope -> synonymTypeDef.endsWith(scope.name()));
        final String name;
        final SynonymScope scope;
        final Node subsetDefNode;
        if (endsWithOptionalSynonymScope) {
            final int scopeSplitIndex = synonymTypeDef.lastIndexOf(' ');
            name = StringUtils.strip(synonymTypeDef.substring(idSplitIndex, scopeSplitIndex).trim(), "\"");
            scope = SynonymScope.valueOf(synonymTypeDef.substring(scopeSplitIndex + 1));
            subsetDefNode = graph.addNode("SynonymType", ID_PROPERTY, id, "name", name, "scope", scope.name());
        } else {
            name = StringUtils.strip(synonymTypeDef.substring(idSplitIndex).trim(), "\"");
            subsetDefNode = graph.addNode("SynonymType", ID_PROPERTY, id, "name", name);
        }
        graph.addEdge(headerNode, subsetDefNode, "HAS_SYNONYM_TYPE");
    }

    private void exportHeaderIdspaces(final Graph graph, final OboHeader header, final Node headerNode) {
        final String[] idspaces = header.getIdspaces();
        if (idspaces != null)
            for (final String idspace : idspaces)
                exportHeaderIdspace(graph, headerNode, idspace);
    }

    private void exportHeaderIdspace(final Graph graph, final Node headerNode, final String idspace) {
        final String[] parts = StringUtils.split(idspace, " ", 3);
        final Node idspaceNode;
        if (parts.length == 3) {
            final String name = StringUtils.strip(parts[2].trim(), "\"");
            idspaceNode = graph.addNode("Idspace", ID_PROPERTY, parts[0], "iri", parts[1], "name", name);
        } else
            idspaceNode = graph.addNode("Idspace", ID_PROPERTY, parts[0], "iri", parts[1]);
        graph.addEdge(headerNode, idspaceNode, "HAS_IDSPACE");
    }

    private void exportEntries(final boolean ignoreObsolete, final Graph graph, final OboReader reader) {
        final Map<String, Map<String, List<EdgeCacheEntry>>> relationCache = new HashMap<>();
        for (final OboEntry entry : reader) {
            if (ignoreObsolete && Boolean.TRUE.equals(entry.isObsolete()))
                continue;
            if (entry instanceof OboTerm)
                exportTerm(graph, (OboTerm) entry, relationCache);
            else if (entry instanceof OboTypedef)
                exportTypedef(graph, (OboTypedef) entry, relationCache);
            else if (entry instanceof OboInstance)
                exportInstance(graph, (OboInstance) entry, relationCache);
        }
        //System.out.println(relationCache.size());
    }

    private void exportTerm(final Graph graph, final OboTerm term,
                            final Map<String, Map<String, List<EdgeCacheEntry>>> relationCache) {
        final NodeBuilder builder = graph.buildNode().withLabel(term.getType());
        populateBuilderWithEntry(builder, term);
        builder.withPropertyIfNotNull("builtin", term.builtin());
        final Node node = builder.build();
        handleRelationshipsWithRelId(graph, term.getRelationships(), node, relationCache);
        handleRelationships(graph, term.isA(), "IS_A", node, relationCache);
        handleRelationships(graph, term.equivalentTo(), "EQUIVALENT_TO", node, relationCache);
        handleRelationships(graph, term.disjointFrom(), "DISJOINT_FROM", node, relationCache);
        handleRelationships(graph, term.unionOf(), "UNION_OF", node, relationCache);
        handleRelationships(graph, term.consider(), "CONSIDER", node, relationCache);
        handleRelationships(graph, term.replacedBy(), "REPLACED_BY", node, relationCache);
    }

    private void populateBuilderWithEntry(final NodeBuilder builder, final OboEntry entry) {
        builder.withProperty(ID_PROPERTY, entry.getId());
        builder.withPropertyIfNotNull("def", entry.getDef());
        builder.withPropertyIfNotNull("name", entry.getName());
        builder.withPropertyIfNotNull("namespace", entry.getNamespace());
        builder.withPropertyIfNotNull("subsets", entry.getSubsets());
        builder.withPropertyIfNotNull("comment", entry.getComment());
        builder.withPropertyIfNotNull("created_by", entry.getCreatedBy());
        builder.withPropertyIfNotNull("creation_date", entry.getCreationDate());
        builder.withPropertyIfNotNull("xrefs", entry.getXrefs());
        builder.withPropertyIfNotNull("alt_ids", entry.getAltIds());
        builder.withPropertyIfNotNull("property_values", entry.getPropertyValues());
        builder.withProperty("obsolete", entry.isObsolete());
        builder.withProperty("anonymous", entry.isAnonymous());
    }

    private void handleRelationshipsWithRelId(final Graph graph, final String[] relationships, final Node entryNode,
                                              final Map<String, Map<String, List<EdgeCacheEntry>>> relationCache) {
        if (relationships != null) {
            final String relationName = "HAS_RELATIONSHIP";
            for (final String relationship : relationships) {
                if (relationship == null)
                    continue;
                final String[] parts = StringUtils.split(relationship, " ", 2);
                final String relationId = parts[0];
                final String targetId = parts[1];
                final Node targetNode = graph.findNode(ID_PROPERTY, targetId);
                if (targetNode == null) {
                    addRelationshipToCache(relationCache, entryNode.getId(), targetId, relationName, "rel_id",
                                           relationId);
                } else
                    graph.addEdge(entryNode, targetNode, relationName, "rel_id", relationId);
            }
        }
        handleCachedRelationshipsForNode(graph, entryNode, relationCache);
    }

    private void addRelationshipToCache(final Map<String, Map<String, List<EdgeCacheEntry>>> relationCache,
                                        final long sourceNodeId, final String targetId, final String relationName,
                                        final String propertyKey, final String propertyValue) {
        relationCache.putIfAbsent(targetId, new HashMap<>());
        relationCache.get(targetId).putIfAbsent(relationName, new ArrayList<>());
        final EdgeCacheEntry cacheEntry = new EdgeCacheEntry();
        cacheEntry.sourceNodeId = sourceNodeId;
        cacheEntry.propertyKey = propertyKey;
        cacheEntry.propertyValue = propertyValue;
        relationCache.get(targetId).get(relationName).add(cacheEntry);
    }

    private void handleCachedRelationshipsForNode(final Graph graph, final Node node,
                                                  final Map<String, Map<String, List<EdgeCacheEntry>>> relationCache) {
        final String targetId = node.getProperty(ID_PROPERTY);
        if (targetId != null && relationCache.containsKey(targetId)) {
            final Map<String, List<EdgeCacheEntry>> relations = relationCache.get(targetId);
            for (final String key : relations.keySet())
                for (final EdgeCacheEntry cacheEntry : relations.get(key))
                    if (cacheEntry.propertyKey != null && cacheEntry.propertyValue != null)
                        graph.addEdge(cacheEntry.sourceNodeId, node, key, cacheEntry.propertyKey,
                                      cacheEntry.propertyValue);
                    else
                        graph.addEdge(cacheEntry.sourceNodeId, node, key);
            relationCache.remove(targetId);
        }
    }

    private void handleRelationships(final Graph graph, final String[] targetIds, final String relationName,
                                     final Node entryNode,
                                     final Map<String, Map<String, List<EdgeCacheEntry>>> relationCache) {
        if (targetIds != null) {
            for (final String targetId : targetIds) {
                if (targetId == null)
                    continue;
                String id = targetId;
                String annotation = null;
                if (targetId.contains(" ")) {
                    final String[] parts = StringUtils.split(targetId, " ", 2);
                    id = parts[0];
                    annotation = parts[1];
                }
                final Node targetNode = graph.findNode(ID_PROPERTY, id);
                if (targetNode == null) {
                    addRelationshipToCache(relationCache, entryNode.getId(), id, relationName, "annotation",
                                           annotation);
                } else {
                    if (annotation != null)
                        graph.addEdge(entryNode, targetNode, relationName, "annotation", annotation);
                    else
                        graph.addEdge(entryNode, targetNode, relationName);
                }
            }
        }
        handleCachedRelationshipsForNode(graph, entryNode, relationCache);
    }

    private void exportTypedef(final Graph graph, final OboTypedef typedef,
                               final Map<String, Map<String, List<EdgeCacheEntry>>> relationCache) {
        final NodeBuilder builder = graph.buildNode().withLabel(typedef.getType());
        populateBuilderWithEntry(builder, typedef);
        builder.withPropertyIfNotNull("builtin", typedef.builtin());
        builder.withPropertyIfNotNull("is_class_level", typedef.isClassLevel());
        builder.withPropertyIfNotNull("is_inverse_functional", typedef.isInverseFunctional());
        builder.withPropertyIfNotNull("is_functional", typedef.isFunctional());
        builder.withPropertyIfNotNull("is_transitive", typedef.isTransitive());
        builder.withPropertyIfNotNull("is_anti_symmetric", typedef.isAntiSymmetric());
        builder.withPropertyIfNotNull("is_symmetric", typedef.isSymmetric());
        builder.withPropertyIfNotNull("is_reflexive", typedef.isReflexive());
        builder.withPropertyIfNotNull("is_cyclic", typedef.isCyclic());
        builder.withPropertyIfNotNull("is_metadata_tag", typedef.isMetadataTag());
        builder.withPropertyIfNotNull("expand_assertion_to", typedef.expandAssertionTo());
        builder.withPropertyIfNotNull("expand_expression_to", typedef.expandExpressionTo());
        final Node node = builder.build();
        handleRelationshipsWithRelId(graph, typedef.getRelationships(), node, relationCache);
        handleRelationships(graph, typedef.intersectionOf(), "INTERSECTION_OF", node, relationCache);
        handleRelationships(graph, typedef.holdsOverChain(), "HOLDS_OVER_CHAIN", node, relationCache);
        handleRelationships(graph, typedef.equivalentToChain(), "EQUIVALENT_TO_CHAIN", node, relationCache);
        handleRelationships(graph, typedef.isA(), "IS_A", node, relationCache);
        handleRelationships(graph, typedef.equivalentTo(), "EQUIVALENT_TO", node, relationCache);
        handleRelationships(graph, typedef.disjointFrom(), "DISJOINT_FROM", node, relationCache);
        handleRelationships(graph, typedef.unionOf(), "UNION_OF", node, relationCache);
        handleRelationships(graph, new String[]{typedef.getDomain()}, "HAS_DOMAIN", node, relationCache);
        handleRelationships(graph, new String[]{typedef.getRange()}, "HAS_RANGE", node, relationCache);
        handleRelationships(graph, typedef.consider(), "CONSIDER", node, relationCache);
        handleRelationships(graph, typedef.inverseOf(), "INVERSE_OF", node, relationCache);
        handleRelationships(graph, typedef.transitiveOver(), "TRANSITIVE_OVER", node, relationCache);
        handleRelationships(graph, typedef.disjointOver(), "DISJOINT_OVER", node, relationCache);
        handleRelationships(graph, typedef.replacedBy(), "REPLACED_BY", node, relationCache);
    }

    private void exportInstance(final Graph graph, final OboInstance instance,
                                final Map<String, Map<String, List<EdgeCacheEntry>>> relationCache) {
        final NodeBuilder builder = graph.buildNode().withLabel(instance.getType());
        populateBuilderWithEntry(builder, instance);
        final Node node = builder.build();
        handleRelationshipsWithRelId(graph, instance.getRelationships(), node, relationCache);
        handleRelationships(graph, instance.consider(), "CONSIDER", node, relationCache);
        handleRelationships(graph, new String[]{instance.instanceOf()}, "INSTANCE_IF", node, relationCache);
        handleRelationships(graph, instance.replacedBy(), "REPLACED_BY", node, relationCache);
    }

    protected abstract String getOntologyFileName();

    protected final boolean ignoreObsolete(final Workspace workspace) {
        final Map<String, String> properties = dataSource.getProperties(workspace);
        return "true".equalsIgnoreCase(properties.get("ignoreObsolete"));
    }
}
