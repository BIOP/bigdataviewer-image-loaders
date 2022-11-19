package ch.epfl.biop.bdv.img.omero.command;

import net.imagej.omero.OMEROServer;
import net.imagej.omero.OMEROService;
import net.imagej.omero.OMEROSession;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Plugin(type = Command.class,
        menuPath = "Plugins>BIOP>OMERO>Omero - Disconnect",
        description = "description")

public class OmeroDisconnectCommand implements Command {

    final private static Logger logger = LoggerFactory.getLogger(
            OmeroDisconnectCommand.class);

    @Parameter
    OMEROService omeroService;

    @Parameter(label = "OMERO host")
    String host;

    @Parameter(type = ItemIO.OUTPUT)
    Boolean success;

    @Parameter(type = ItemIO.OUTPUT)
    Exception error;

    public void run() {
        try {
            OMEROSession session = omeroService.session(new OMEROServer(host, true));
            logger.info("Session active ? : " + session.getGateway().isConnected());
            if (session.getGateway().isConnected()) {
                session.getGateway().disconnect();
            }
            success = true;
        } catch (Exception e) {
            error = e;
            logger.error(e.getMessage());
            success = false;
        }
    }

}
