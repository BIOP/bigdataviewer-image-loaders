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

import bdv.util.BdvFunctions;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsConvertFilesToSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({ "Unused", "CanBeFinal" })
@Plugin(type = Command.class,
	menuPath = "Plugins>BigDataViewer>Bio-Formats>Open File with Bio-Formats",
	description = "Support bioformats multiresolution api. Attempts to set colors based " +
		"on bioformats metadata. Do not attempt auto contrast.")
public class StandaloneOpenFileWithBigdataviewerBioformatsBridgeCommand
	implements Command
{

	@Parameter(required = false, label = "Physical units of the dataset",
		choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
	public String unit = "MILLIMETER";

	@Parameter(label = "File to open", style = "open")
	File file;

	@Parameter(required = false,
		label = "Split RGB channels if you have 16 bits RGB images")
	boolean splitrgbchannels = true; // Split rgb channels to allow for best
																		// compatibility (RGB 16 bits)

	public void run() {

		BioformatsBigdataviewerBridgeDatasetCommand settings =
			new BioformatsBigdataviewerBridgeDatasetCommand();
		settings.splitrgbchannels = splitrgbchannels;
		settings.unit = unit;

		List<BioFormatsBdvOpener> openers = new ArrayList<>();
		openers.add(settings.getOpener(file));
		final AbstractSpimData spimData = BioFormatsConvertFilesToSpimData
			.getSpimData(openers);
		BdvFunctions.show(spimData);
	}

}
