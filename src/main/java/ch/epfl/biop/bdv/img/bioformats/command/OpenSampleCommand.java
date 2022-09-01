/*-
 * #%L
 * Commands and function for opening, conversion and easy use of bioformats format into BigDataViewer
 * %%
 * Copyright (C) 2019 - 2022 Nicolas Chiaruttini, BIOP, EPFL
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the BIOP nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package ch.epfl.biop.bdv.img.bioformats.command;

import ch.epfl.biop.bdv.img.bioformats.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsToSpimData;
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

			BioFormatsBdvOpener opener20 = BioFormatsBdvOpener.getOpener().location(
				f20).auto().centerPositionConvention().millimeter()
				.voxSizeReferenceFrameLength(millimeter).positionReferenceFrameLength(
					micron);

			BioFormatsBdvOpener opener60 = BioFormatsBdvOpener.getOpener().location(
				f60).auto().centerPositionConvention().millimeter()
				.voxSizeReferenceFrameLength(millimeter).positionReferenceFrameLength(
					micron);

			ArrayList<BioFormatsBdvOpener> openers = new ArrayList<>();
			openers.add(opener20);
			openers.add(opener60);

			spimData = BioFormatsToSpimData.getSpimData(openers);

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

					spimData = BioFormatsToSpimData.getSpimData(BioFormatsBdvOpener
						.getOpener().location(file).auto().voxSizeReferenceFrameLength(
							new Length(1, UNITS.MILLIMETER)).positionReferenceFrameLength(
								new Length(1, UNITS.MILLIMETER)));

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
