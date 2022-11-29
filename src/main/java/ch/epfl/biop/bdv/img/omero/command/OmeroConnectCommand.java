package ch.epfl.biop.bdv.img.omero.command;

import net.imagej.omero.OMEROCredentials;
import net.imagej.omero.OMEROServer;
import net.imagej.omero.OMEROService;
import net.imagej.omero.OMEROSession;
import omero.gateway.ServerInformation;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Plugin(type = Command.class,
        menuPath = "Plugins>BIOP>OMERO>Omero - Connect",
        description = "Connect to an OMERO server", initializer = "init")

public class OmeroConnectCommand implements Command {

    final private static Logger logger = LoggerFactory.getLogger(
            OmeroConnectCommand.class);

    public static String message_in = "Please enter your OMERO credentials";

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
    String message = "Please enter your OMERO credentials";

    @Parameter
    OMEROService omeroService;

    @Parameter(label = "OMERO host")
    String host = "omero-server.epfl.ch";

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

    public void init() {
        message = message_in;
    }

}
