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

package ch.epfl.biop.bdv.img.legacy.qupath.struct;

import java.util.List;
import java.net.URI;


@SuppressWarnings({ "unused" }) // Used for deserialization
@Deprecated
public class MinimalQuPathProject {

	public String version;

	public URI uri;

	public int lastID;

	public List<ImageEntry> images;

	public static class ImageEntry {

		public ServerBuilderEntry serverBuilder;
		public int entryID;
		public String randomizedName;
		public String imageName;
	}

	public static class ServerBuilderMetadata {

		public String name;
		public int width;
		public int height;
		public int sizeZ;
		public int sizeT;
		public String channelType;
		public boolean isRGB;
		public String pixelType;
		// "levels": (ignored)
		public List<ChannelInfo> channels;
		public PixelCalibrations pixelCalibration;

	}

	public static class ChannelInfo {

		int color;
		String name;
	}

	public static class ServerBuilderEntry {

		public String builderType; // "uri" or "rotated"
		public ServerBuilderEntry builder;
		public String rotation; // for "rotated builder"
		public String providerClassName; // "qupath.lib.images.servers.bioformats.BioFormatsServerBuilder",
		public URI uri;
		public List<String> args;
		public ServerBuilderMetadata metadata;
	}

	public static class PixelCalibrations {

		public PixelCalibration pixelWidth;
		public PixelCalibration pixelHeight;
		public PixelCalibration zSpacing;
	}

	public static class PixelCalibration {

		public double value;
		public String unit;
	}

}
