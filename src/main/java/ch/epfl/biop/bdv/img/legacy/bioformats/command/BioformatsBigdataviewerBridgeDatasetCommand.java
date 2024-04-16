/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2024 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.legacy.bioformats.command;

import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsTools;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Deprecated
@SuppressWarnings({ "unused", "CanBeFinal" })
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

		Unit<Length> bfUnit = BioFormatsTools.getUnitFromString(unit);

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

		// Not sure if it is useful here because the metadata location is handled
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
