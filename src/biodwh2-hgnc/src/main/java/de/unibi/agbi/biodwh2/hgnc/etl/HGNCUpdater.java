package de.unibi.agbi.biodwh2.hgnc.etl;

import de.unibi.agbi.biodwh2.core.etl.Updater;
import de.unibi.agbi.biodwh2.core.model.Version;
import de.unibi.agbi.biodwh2.core.net.AnonymousFTPClient;

import java.time.LocalDateTime;

public class HGNCUpdater extends Updater {
    public HGNCUpdater() {
    }

    @Override
    public boolean update() {
        return false;
    }

    @Override
    public Version getNewestVersion() {
        String filePath = "pub/databases/genenames/new/tsv/hgnc_complete_set.txt";
        AnonymousFTPClient ftpClient = new AnonymousFTPClient();
        boolean isConnected = ftpClient.tryConnect("ftp.ebi.ac.uk");
        if (!isConnected)
            return null;
        LocalDateTime dateTime = ftpClient.getModificationTimeFromServer(filePath);
        return dateTime != null ? convertDateTimeToVersion(dateTime) : null;
    }
}
