/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2025 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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
package ch.epfl.biop.bdv.img;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@Plugin(type = Command.class,
        description = "Allows fixing invalid file paths in a BDV dataset by providing replacement paths.",
        initializer = "init")
public class FixFilePathsCommand implements Command {

    public static String message_in = "";

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = message_in;

    @Parameter(label = "Invalid Files",
            description = "The files with invalid paths that need to be fixed.",
            type = ItemIO.BOTH)
    File[] invalidFilePaths;

    @Parameter(label = "Replacement Files",
            description = "The replacement files in the same order as the invalid files.",
            type = ItemIO.BOTH)
    File[] fixedFilePaths;

    @Override
    public void run() {

    }

    public void init() {
        message = message_in;
    }
}
