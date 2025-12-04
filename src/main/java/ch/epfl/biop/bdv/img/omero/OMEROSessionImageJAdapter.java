package ch.epfl.biop.bdv.img.omero;

import ch.epfl.biop.bdv.img.omero.entity.DefaultOMEROSession;
import net.imagej.omero.OMEROSession;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;

public class OMEROSessionImageJAdapter implements IOMEROSession {

    DefaultOMEROSession session;

    public OMEROSessionImageJAdapter(OMEROSession session) {
        this.session = new DefaultOMEROSession(session.getGateway(), session.getSecurityContext());
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
