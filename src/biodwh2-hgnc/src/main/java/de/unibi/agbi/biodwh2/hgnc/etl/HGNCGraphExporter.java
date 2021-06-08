package de.unibi.agbi.biodwh2.hgnc.etl;

import de.unibi.agbi.biodwh2.core.Workspace;
import de.unibi.agbi.biodwh2.core.etl.GraphExporter;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.core.model.graph.IndexDescription;
import de.unibi.agbi.biodwh2.hgnc.HGNCDataSource;
import de.unibi.agbi.biodwh2.hgnc.model.Gene;

public class HGNCGraphExporter extends GraphExporter<HGNCDataSource> {
    public HGNCGraphExporter(final HGNCDataSource dataSource) {
        super(dataSource);
    }

    @Override
    public long getExportVersion() {
        return 1;
    }

    @Override
    protected boolean exportGraph(final Workspace workspace, final Graph graph) {
        graph.addIndex(IndexDescription.forNode("Gene", "hgnc_id", IndexDescription.Type.UNIQUE));
        graph.addIndex(IndexDescription.forNode("Gene", "symbol", IndexDescription.Type.UNIQUE));
        for (final Gene gene : dataSource.genes)
            graph.addNodeFromModel(gene);
        return true;
    }
}
