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
import ch.epfl.biop.bdv.img.bioformats.BioFormatsMetaDataHelper;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({ "Unused", "CanBeFinal" })
public class BioformatsBigdataviewerBridgeDatasetCommand implements Command {

	static public Map<String, Object> getDefaultParameters() {
		Map<String, Object> def = new HashMap<>();
		def.put("unit", "MILLIMETER");
		def.put("splitrgbchannels", false);
		def.put("positioniscenter", "AUTO");
		def.put("switchzandc", "AUTO");
		def.put("flippositionx", "AUTO");
		def.put("flippositiony", "AUTO");
		def.put("flippositionz", "AUTO");
		def.put("usebioformatscacheblocksize", true);
		def.put("cachesizex", 512);
		def.put("cachesizey", 512);
		def.put("cachesizez", 1);
		def.put("refframesizeinunitlocation", 1);
		def.put("refframesizeinunitvoxsize", 1);
		def.put("numberofblockskeptinmemory", -1);
		return def;
	}

	// Parameter for dataset creation
	@Parameter(required = false, label = "Physical units of the dataset",
		choices = { "MILLIMETER", "MICROMETER", "NANOMETER" })
	public String unit = "MILLIMETER";

	@Parameter(required = false, label = "Split RGB channels")
	public boolean splitrgbchannels = false;

	@Parameter(required = false, choices = { "AUTO", "TRUE", "FALSE" })
	public String positioniscenter = "AUTO";

	@Parameter(required = false, choices = { "AUTO", "TRUE", "FALSE" })
	public String switchzandc = "AUTO";

	@Parameter(required = false, choices = { "AUTO", "TRUE", "FALSE" })
	public String flippositionx = "AUTO";

	@Parameter(required = false, choices = { "AUTO", "TRUE", "FALSE" })
	public String flippositiony = "AUTO";

	@Parameter(required = false, choices = { "AUTO", "TRUE", "FALSE" })
	public String flippositionz = "AUTO";

	@Parameter(required = false)
	public boolean usebioformatscacheblocksize = true;

	@Parameter(required = false)
	public int cachesizex = 512, cachesizey = 512, cachesizez = 1;

	@Parameter(required = false)
	public int numberofblockskeptinmemory = -1;

	@Parameter(required = false,
		label = "Reference frame size in unit (position)")
	public double refframesizeinunitlocation = 1;

	@Parameter(required = false,
		label = "Reference frame size in unit (voxel size)")
	public double refframesizeinunitvoxsize = 1;

	public BioFormatsBdvOpener getOpener(String datalocation) {

		Unit<Length> bfUnit = BioFormatsMetaDataHelper.getUnitFromString(unit);

		Length positionReferenceFrameLength = new Length(refframesizeinunitlocation,
			bfUnit);
		Length voxSizeReferenceFrameLength = new Length(refframesizeinunitvoxsize,
			bfUnit);

		BioFormatsBdvOpener opener = BioFormatsBdvOpener.getOpener().location(
			datalocation).unit(unit).auto().ignoreMetadata();

		if (!switchzandc.equals("AUTO")) {
			opener = opener.switchZandC(switchzandc.equals("TRUE"));
		}

		if (!usebioformatscacheblocksize) {
			opener = opener.cacheBlockSize(cachesizex, cachesizey, cachesizez);
		}

		if (numberofblockskeptinmemory > 0) {
			opener = opener.cacheBounded(numberofblockskeptinmemory);
		}

		// Not sure it is useful here because the metadata location is handled
		// somewhere else
		if (!positioniscenter.equals("AUTO")) {
			if (positioniscenter.equals("TRUE")) {
				opener = opener.centerPositionConvention();
			}
			else {
				opener = opener.cornerPositionConvention();
			}
		}

		if (!flippositionx.equals("AUTO")) {
			if (flippositionx.equals("TRUE")) {
				opener = opener.flipPositionX();
			}
		}

		if (!flippositiony.equals("AUTO")) {
			if (flippositiony.equals("TRUE")) {
				opener = opener.flipPositionY();
			}
		}

		if (!flippositionz.equals("AUTO")) {
			if (flippositionz.equals("TRUE")) {
				opener = opener.flipPositionZ();
			}
		}

		opener = opener.unit(unit);

		opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);

		opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);

		if (splitrgbchannels) opener = opener.splitRGBChannels();

		return opener;
	}

	public BioFormatsBdvOpener getOpener(File f) {
		return getOpener(f.getAbsolutePath());
	}

	@Override
	public void run() {

	}
}
