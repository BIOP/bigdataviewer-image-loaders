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

import bdv.util.BdvFunctions;
import loci.common.DebugTools;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;

import javax.swing.SwingUtilities;

public class OpenBdvXmlDataset {
    static public void main(String... args) throws Exception {
        // Arrange
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        SwingUtilities.invokeAndWait(() -> ij.ui().showUI());
        // DebugTools.enableLogging("DEBUG");
        DebugTools.enableLogging("OFF");
        AbstractSpimData<?> sd = new XmlIoSpimData().load("C:\\Users\\chiarutt\\Desktop\\ABBA_Workshop\\PCB06-DemoProject\\abba\\_bdvdataset_0.xml");
        BdvFunctions.show(sd);
    }
}
