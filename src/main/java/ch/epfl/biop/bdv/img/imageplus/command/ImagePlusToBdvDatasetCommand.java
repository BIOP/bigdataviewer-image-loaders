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

package ch.epfl.biop.bdv.img.imageplus.command;

import ch.epfl.biop.bdv.img.imageplus.ImagePlusToSpimData;
import ij.ImagePlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@SuppressWarnings({ "unused", "CanBeFinal" })
@Plugin(type = Command.class,
		menuPath = "Plugins>BigDataViewer>ImagePlus>Open Image",
		description = "Creates a BDV dataset from the current ImagePlus window.")
public class ImagePlusToBdvDatasetCommand implements Command {

	@Parameter(label = "Input Image",
			description = "The ImagePlus image to convert to a BDV dataset.")
	public ImagePlus image;

	@Parameter(type = ItemIO.OUTPUT,
			label = "BDV Dataset",
			description = "The resulting BDV dataset.")
	AbstractSpimData<?> spimdata;

	@Parameter(label = "Dataset Name",
			description = "Name for the dataset (leave empty to use the image title).",
			persist = false)
	public String datasetname = "";

	@Override
	public void run() {
		datasetname = image.getTitle();
		spimdata = ImagePlusToSpimData.getSpimData(image);
	}
}
