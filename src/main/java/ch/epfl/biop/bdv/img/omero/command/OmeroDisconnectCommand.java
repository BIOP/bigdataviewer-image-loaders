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
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

@Plugin(type = Command.class,
        menuPath = "Plugins>BIOP>OMERO>Omero - Disconnect",
        description = "Disconnects from an OMERO server and closes all sessions.")
public class OmeroDisconnectCommand implements Command {

    final private static Logger logger = LoggerFactory.getLogger(
            OmeroDisconnectCommand.class);

    @Parameter
    OMEROService omeroService;

    @Parameter(label = "OMERO Host",
            description = "The hostname of the OMERO server to disconnect from.")
    String host;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Success",
            description = "True if the disconnection was successful.")
    Boolean success;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Error",
            description = "The exception if disconnection failed, null otherwise.")
    Exception error;

    public void run() {
        try {
            if (!OmeroHelper.hasCachedSession(host)) {
                logger.warn("No session with the host "+host+" was found.");
                success = false;
                return;
            }
            Collection<IOMEROSession> sessions = OmeroHelper.getCachedOMEROSessions(host);

            for (IOMEROSession session: sessions) {
                if (session.getGateway().isConnected()) {
                    session.getGateway().disconnect();
                } else {
                    logger.info("Session on host " + host + " was already disconnected.");
                }
            }

            OmeroHelper.removeCachedSessions(host);

            success = true;
        } catch (Exception e) {
            error = e;
            logger.error(e.getMessage());
            success = false;
        }
    }

}
