package ch.epfl.biop.bdv.img.omero;

import net.imagej.omero.OMEROSession;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;

public class OMEROSessionImageJAdapter implements IOMEROSession {

    private final OMEROSession session;

    public OMEROSessionImageJAdapter(OMEROSession session) {
        this.session = session;
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
