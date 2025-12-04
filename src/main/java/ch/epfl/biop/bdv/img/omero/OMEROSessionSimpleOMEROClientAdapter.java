package ch.epfl.biop.bdv.img.omero;

import ch.epfl.biop.bdv.img.omero.entity.DefaultOMEROSession;
import fr.igred.omero.Client;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;

public class OMEROSessionSimpleOMEROClientAdapter implements IOMEROSession {

    DefaultOMEROSession session;

    public OMEROSessionSimpleOMEROClientAdapter(Client client) {
        this.session = new DefaultOMEROSession(client.getGateway(), client.getCtx());
    }

    @Override
    public Gateway getGateway() {
        return session.getGateway();
    }

    @Override
    public SecurityContext getSecurityContext() {
        return session.getSecurityContext();
    }
}
