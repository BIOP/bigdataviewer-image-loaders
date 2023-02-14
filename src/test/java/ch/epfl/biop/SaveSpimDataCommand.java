/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2023 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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
package ch.epfl.biop;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
@Plugin(type = Command.class,
        menuPath = "Test>Save BDV Dataset")
public class SaveSpimDataCommand implements Command {

    @Parameter
    AbstractSpimData<?> spimData;

    @Parameter(style = "save")
    File file;

    @Override
    public void run() {
        try {
            if (spimData instanceof SpimData) {
                (new XmlIoSpimData()).save((SpimData) spimData, file.getAbsolutePath());
            } else if (spimData instanceof SpimDataMinimal) {
                (new XmlIoSpimDataMinimal()).save((SpimDataMinimal) spimData,
                        file.getAbsolutePath());
            }
        } catch (Exception e) {
            IJ.log(e.getMessage());
        }
    }
}
