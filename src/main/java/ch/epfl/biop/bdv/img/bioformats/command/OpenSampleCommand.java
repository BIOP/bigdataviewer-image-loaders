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

import ch.epfl.biop.bdv.img.ImageToSpimData;
import ch.epfl.biop.bdv.img.OpenerSettings;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsTools;
import ch.epfl.biop.bdv.img.bioformats.samples.DatasetHelper;
import mpicbg.spim.data.generic.AbstractSpimData;
import ome.units.UNITS;
import ome.units.quantity.Length;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;

@SuppressWarnings({ "Unused", "CanBeFinal" })
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer-Playground>BDVDataset>Open sample dataset",
	label = "Open sample datasets",
	description = "Downloads and cache datasets on first open attempt.")

public class OpenSampleCommand implements Command {

	@Parameter(label = "Choose a sample dataset", choices = { "VSI", "JPG_RGB",
		"OLYMPUS_OIR", "LIF", "TIF_TIMELAPSE_3D", "ND2_20X", "ND2_60X",
		"BOTH_ND2" })
	String datasetName;

	@Parameter(type = ItemIO.OUTPUT)
	AbstractSpimData spimData;

	public void run() {
		// Find the datasetname through reflection
		Field[] fields = DatasetHelper.class.getFields();
		if (datasetName.equals("BOTH_ND2")) {
			File f20 = DatasetHelper.getDataset(DatasetHelper.ND2_20X);
			File f60 = DatasetHelper.getDataset(DatasetHelper.ND2_60X);

			Length micron = new Length(1, UNITS.MICROMETER);

			Length millimeter = new Length(1, UNITS.MILLIMETER);

			ArrayList<OpenerSettings> settings = new ArrayList<>();

			for (int i = 0; i< BioFormatsTools.getNSeries(f20); i++) {
				OpenerSettings opener20 = new OpenerSettings()
								.location(f20)
								.centerPositionConvention()
								.millimeter()
								.voxSizeReferenceFrameLength(millimeter)
								.positionReferenceFrameLength(micron)
								.bioFormatsBuilder()
								.setSerie(i)
								.cornerPositionConvention();

				settings.add(opener20);
			}

			for (int i = 0; i< BioFormatsTools.getNSeries(f60); i++) {
				OpenerSettings opener60 = new OpenerSettings()
						.location(f60)
						.centerPositionConvention()
						.millimeter()
						.voxSizeReferenceFrameLength(millimeter)
						.positionReferenceFrameLength(micron)
						.bioFormatsBuilder()
						.setSerie(i)
						.cornerPositionConvention();

				settings.add(opener60);
			}
			//settings.add(opener20);
			//settings.add(opener60);

			spimData = ImageToSpimData.getSpimData(settings);

			return;
		}
		for (Field f : fields) {
			if (f.getName().toUpperCase().equals(datasetName.toUpperCase())) {
				try {
					// Dataset found
					datasetName = (String) f.get(null);
					System.out.println(datasetName);
					if (datasetName.equals(DatasetHelper.VSI)) {
						DatasetHelper.getSampleVSIDataset();
					}

					File file = DatasetHelper.getDataset(datasetName);

					spimData = ImageToSpimData.getSpimData(new OpenerSettings().location(file).voxSizeReferenceFrameLength(
							new Length(1, UNITS.MILLIMETER)).positionReferenceFrameLength(
								new Length(1, UNITS.MILLIMETER)).bioFormatsBuilder());

					return;
				}
				catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}

		System.err.println("Dataset not found!");
	}
}
