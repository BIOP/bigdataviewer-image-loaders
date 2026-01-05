/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package ch.epfl.biop.bdv.img.omero.command;

import ch.epfl.biop.bdv.img.omero.IOMEROSession;
import ch.epfl.biop.bdv.img.omero.OmeroHelper;
import net.imagej.omero.OMEROService;
import omero.gateway.ServerInformation;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("CanBeFinal")
@Plugin(type = Command.class,
        menuPath = "Plugins>BIOP>OMERO>Omero - Connect",
        description = "Connects to an OMERO server using your credentials.",
        initializer = "init")
public class OmeroConnectCommand implements Command {

    final private static Logger logger = LoggerFactory.getLogger(
            OmeroConnectCommand.class);

    public static String message_in = "Please enter your OMERO credentials";

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false)
    String message = "Please enter your OMERO credentials";

    @Parameter
    Context ctx;

    @Parameter
    OMEROService omeroService;

    @Parameter(label = "OMERO Host",
            description = "The hostname or IP address of the OMERO server.")
    String host = "omero-server.epfl.ch";

    @Parameter(label = "Username",
            description = "Your OMERO username.")
    String username;

    @Parameter(label = "Password",
            description = "Your OMERO password.",
            style = "password",
            persist = false)
    String password;

    @Parameter(label = "Port",
            description = "The OMERO Ice port (default is 4064).")
    int port = 4064;

    @Parameter(type = ItemIO.OUTPUT,
            label = "OMERO Session",
            description = "The active OMERO session if connection succeeds.")
    IOMEROSession omeroSession;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Success",
            description = "True if the connection was successful.")
    Boolean success;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Error",
            description = "The exception if connection failed, null otherwise.")
    Exception error;

    public void run() {
        try {
            omeroSession = OmeroHelper.getOMEROSession(host, port, username, password.toCharArray(), ctx);
            password = "";
            logger.info("Session active : " + omeroSession.getGateway().isConnected());
            omeroSession.getSecurityContext().setServerInformation(new ServerInformation(host));
            success = omeroSession.getGateway().isConnected();
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
