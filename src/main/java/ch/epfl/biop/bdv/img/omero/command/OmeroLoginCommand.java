package ch.epfl.biop.bdv.img.omero.command;

import ch.epfl.biop.bdv.img.omero.OmeroTools;
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
            OpenWithBigDataViewerOmeroBridgeCommand.class);

    @Parameter(label = "OMERO host")
    String host;

    @Parameter(label = "Enter your gaspar username")
    String username;

    @Parameter(label = "Enter your gaspar password", style = "password",
            persist = false)
    String password;

    @Parameter(label = "OMERO port")
    int port = 4064;

    @Parameter(type = ItemIO.OUTPUT)
    OmeroTools.GatewayAndSecurityContext gasc;

    @Parameter(type = ItemIO.OUTPUT)
    Boolean success;

    @Parameter(type = ItemIO.OUTPUT)
    DSOutOfServiceException error;

    public void run() {
        gasc = new OmeroTools.GatewayAndSecurityContext();
        try {
            gasc.gateway = OmeroTools.omeroConnect(host, port, username, password);
            password = "";
            logger.info("Session active : " + gasc.gateway.isConnected());
            gasc.securityContext = OmeroTools.getSecurityContext(gasc.gateway);
            gasc.securityContext.setServerInformation(new ServerInformation(host));
            success = true;
        } catch (DSOutOfServiceException e) {
            error = e;
            logger.error(e.getMessage());
            success = false;
        }
    }

}
