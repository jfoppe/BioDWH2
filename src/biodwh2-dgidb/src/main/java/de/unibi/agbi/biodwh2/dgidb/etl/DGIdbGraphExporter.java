package de.unibi.agbi.biodwh2.dgidb.etl;

import de.unibi.agbi.biodwh2.core.Workspace;
import de.unibi.agbi.biodwh2.core.etl.GraphExporter;
import de.unibi.agbi.biodwh2.core.exceptions.ExporterException;
import de.unibi.agbi.biodwh2.core.model.graph.Graph;
import de.unibi.agbi.biodwh2.dgidb.DGIdbDataSource;

public class DGIdbGraphExporter extends GraphExporter<DGIdbDataSource> {
    @Override
    protected boolean exportGraph(final Workspace workspace, final DGIdbDataSource dataSource,
                                  final Graph graph) throws ExporterException {
        return false;
    }
}
