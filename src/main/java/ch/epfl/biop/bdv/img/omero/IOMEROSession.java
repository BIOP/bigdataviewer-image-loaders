package ch.epfl.biop.bdv.img.omero;

import omero.gateway.Gateway;
import omero.gateway.SecurityContext;

/** Abstraction layer for OMEROSession,
 * should implement an adaptor for Simple OMERO Client
 * and an adaptor for imagej-omero
 *
 */
public interface IOMEROSession {

    Gateway getGateway();

    SecurityContext getSecurityContext();
}
