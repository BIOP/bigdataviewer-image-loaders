package ch.epfl.biop.bdv.img.omero;

import fr.igred.omero.Client;
import net.imagej.omero.OMEROSession;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;

public class OMEROSessionSimpleOMEROClientAdapter implements IOMEROSession {

    private final Client client;

    public OMEROSessionSimpleOMEROClientAdapter(Client client) {
        this.client = client;
    }

    @Override
    public Gateway getGateway() {
        return client.getGateway();
    }

    @Override
    public SecurityContext getSecurityContext() {
        return client.getCtx();
    }
}
