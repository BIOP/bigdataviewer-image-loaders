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

package ch.epfl.biop.bdv.img.bioformats.command;

import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({ "Unused", "CanBeFinal" })
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Create BDV Dataset [Bio-Formats]",
	description = "Bridge between Bio-Formats (BioFormats) and BigDataViewer. You can create a BDV dataset" +
			" from a set of Bio-Formats supported files.")
public class CreateBdvDatasetBioFormatsCommand implements
	Command
{

	@Parameter(label = "Name of this dataset")
	public String datasetname = "dataset";

	@Parameter(required = false, label = "Physical units of the dataset",
		choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
	public String unit = "MILLIMETER";

	@Parameter(required = false,
			label = "Image metadata location = ", choices = {"CENTER", "TOP LEFT"})
	String position_convention = "CENTER"; // Split rgb channels to allow for best

	@Parameter(label = "Dataset files")
	File[] files;

	@Parameter(label = "Split RGB channels")
	boolean splitrgbchannels;

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData spimdata;

	@Parameter
	Context ctx;

	public void run() {
		List<OpenerSettings> openerSettings = new ArrayList<>();
		for (File f : files) {
			int nSeries = BioFormatsTools.getNSeries(f);
			for (int i = 0; i < nSeries; i++) {
				openerSettings.add(
						OpenerSettings.getDefaultSettings(OpenerSettings.OpenerType.BIOFORMATS)
								.location(f)
								.setSerie(i)
								.unit(unit)
								.splitRGBChannels(splitrgbchannels)
								.positionConvention(position_convention)
								.cornerPositionConvention()
								.context(ctx));
			}
		}
		spimdata = OpenersToSpimData.getSpimData(openerSettings);
	}

}
