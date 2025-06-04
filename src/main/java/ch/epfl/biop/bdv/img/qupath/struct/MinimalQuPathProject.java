/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2025 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.qupath.struct;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
		//public int indexInQuPathProject; // not in the initial QuPath project => should be added manually
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

		public int color;
		public String name;
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

		public double value = 1.0;
		public String unit = "µm";
	}

	public static class EmptyImageEntry extends ImageEntry {
		public EmptyImageEntry(int entryID, int defaultNumberOfChannels) {
			super();

			imageName = "(deleted entry "+entryID+")";
			randomizedName = "";
			serverBuilder = new MinimalQuPathProject.ServerBuilderEntry();
			serverBuilder.builderType = "Empty";
			serverBuilder.metadata = new ServerBuilderMetadata();
			serverBuilder.metadata.pixelType = "UINT8";
			serverBuilder.metadata.isRGB = false;
			serverBuilder.metadata.pixelCalibration = new PixelCalibrations();
			serverBuilder.metadata.pixelCalibration.pixelHeight = new PixelCalibration();
			serverBuilder.metadata.pixelCalibration.pixelWidth = new PixelCalibration();
			serverBuilder.metadata.pixelCalibration.zSpacing = new PixelCalibration();
			serverBuilder.metadata.channelType = "DEFAULT";
			serverBuilder.metadata.height = 512;
			serverBuilder.metadata.width = 512;
			serverBuilder.metadata.name = imageName;
			serverBuilder.metadata.sizeT = 1;
			serverBuilder.metadata.sizeZ = 1;
			serverBuilder.metadata.channels = new ArrayList<>(defaultNumberOfChannels);
			for (int iCh = 0; iCh<defaultNumberOfChannels; iCh++) {
				ChannelInfo chInfo = new ChannelInfo();
				chInfo.name = "Channel "+iCh;
				chInfo.color =  -16711936;
				serverBuilder.metadata.channels.add(chInfo);
			}
		}
	}
}
