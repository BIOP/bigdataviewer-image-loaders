package ch.epfl.biop.bdv.img.omero.command;

import IceInternal.Ex;
import ch.epfl.biop.bdv.img.omero.OmeroTools;
import net.imagej.omero.OMEROCredentials;
import net.imagej.omero.OMEROException;
import net.imagej.omero.OMEROServer;
import net.imagej.omero.OMEROService;
import net.imagej.omero.OMEROSession;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.ServerInformation;
import omero.gateway.exception.DSOutOfServiceException;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Plugin(type = Command.class,
        menuPath = "Plugins>BIOP>OMERO>Omero - Login",
        description = "description")

public class OmeroLoginCommand implements Command {

    final private static Logger logger = LoggerFactory.getLogger(
            OmeroLoginCommand.class);

    @Parameter
    OMEROService omeroService;

    @Parameter(label = "OMERO host")
    String host;

    @Parameter(label = "Enter your gaspar username")
    String username;

    @Parameter(label = "Enter your gaspar password", style = "password",
            persist = false)
    String password;

    int port = 4064;

    @Parameter(type = ItemIO.OUTPUT)
    OMEROSession omeroSession;

    @Parameter(type = ItemIO.OUTPUT)
    Boolean success;

    @Parameter(type = ItemIO.OUTPUT)
    Exception error;

    public void run() {
        try {
            omeroSession = omeroService.session(new OMEROServer(host,port), new OMEROCredentials(username, password));
            password = "";
            logger.info("Session active : " + omeroSession.getGateway().isConnected());
            omeroSession.getSecurityContext().setServerInformation(new ServerInformation(host));
            success = true;
        } catch (Exception e) {
            error = e;
            logger.error(e.getMessage());
            success = false;
        }
    }

}
