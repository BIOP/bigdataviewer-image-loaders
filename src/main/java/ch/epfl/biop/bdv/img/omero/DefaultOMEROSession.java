package ch.epfl.biop.bdv.img.omero;

import omero.gateway.Gateway;
import omero.gateway.SecurityContext;

public class DefaultOMEROSession implements IOMEROSession {

    final Gateway gateway;
    final SecurityContext context;

    public DefaultOMEROSession(Gateway gateway, SecurityContext context) {
        this.gateway = gateway;
        this.context = context;
    }

    @Override
    public Gateway getGateway() {
        return gateway;
    }

    @Override
    public SecurityContext getSecurityContext() {
        return context;
    }
}
