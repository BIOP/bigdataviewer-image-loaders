/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

import loci.common.DebugTools;
import net.imagej.ImageJ;
import sc.fiji.bdvpg.scijava.command.source.SourcesRemoverCommand;

import javax.swing.SwingUtilities;

public class SimpleIJLaunch {
	// -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
	static public void main(String... args) throws Exception {
		// Arrange
		// create the ImageJ application context with all available services
		final ImageJ ij = new ImageJ();
		//DebugTools.enableLogging("DEBUG");
		//SwingUtilities.invokeAndWait(() ->);
		ij.ui().showUI();
		//DebugTools.enableLogging("INFO");
	}
}
