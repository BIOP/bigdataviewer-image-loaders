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

package ch.epfl.biop.bdv.img.bioformats;

import bdv.img.cache.CacheArrayLoader;
import ch.epfl.biop.bdv.img.ResourcePool;
import loci.formats.IFormatReader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// Copied from N5 Array Loader
public class BioFormatsArrayLoaders {

	abstract static class BioformatsArrayLoader {

		final protected ResourcePool<IFormatReader> readerPool;
		final protected int series;
		final protected int channel;
		final protected boolean switchZandC;

		public BioformatsArrayLoader(ResourcePool<IFormatReader> readerPool, int series, int channel,
			boolean switchZandC)
		{
			this.readerPool = readerPool;
			this.series = series;
			this.channel = channel;
			this.switchZandC = switchZandC;
		}

	}

	public static class BioFormatsUnsignedByteArrayLoader extends
		BioformatsArrayLoader implements CacheArrayLoader<VolatileByteArray>
	{

		public BioFormatsUnsignedByteArrayLoader(ResourcePool<IFormatReader> readerPool, int series,
			int channel, boolean switchZandC)
		{
			super(readerPool, series, channel, switchZandC);
		}

		@Override
		public VolatileByteArray loadArray(int timepoint, int setup, int level,
			int[] dimensions, long[] min) throws InterruptedException
		{
			try {
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(series);
				reader.setResolution(level);
				int minX = (int) min[0];
				int minY = (int) min[1];
				int minZ = (int) min[2];
				int maxX = Math.min(minX + dimensions[0], reader.getSizeX());
				int maxY = Math.min(minY + dimensions[1], reader.getSizeY());
				int maxZ = Math.min(minZ + dimensions[2], reader.getSizeZ());
				int w = maxX - minX;
				int h = maxY - minY;
				int d = maxZ - minZ;
				int nElements = (w * h * d);
				if (dimensions[2] == 1) {
					// Optimisation (maybe useful ? should avoid an array allocation and
					// the ByteBuffer overhead
					byte[] bytes = reader.openBytes(switchZandC ? reader.getIndex(channel,
						minZ, timepoint) : reader.getIndex(minZ, channel, timepoint), minX,
						minY, w, h);
					readerPool.recycle(reader);
					return new VolatileByteArray(bytes, true);
				}
				else {
					byte[] bytes = new byte[nElements];
					int offset = 0;
					for (int z = minZ; z < maxZ; z++) {
						byte[] bytesCurrentPlane = reader.openBytes(switchZandC ? reader
							.getIndex(channel, z, timepoint) : reader.getIndex(z, channel,
								timepoint), minX, minY, w, h);
						System.arraycopy(bytesCurrentPlane, 0, bytes, offset, nElements);
						offset += nElements;
					}
					readerPool.recycle(reader);
					return new VolatileByteArray(bytes, true);
				}
			}
			catch (Exception e) {
				throw new InterruptedException(e.getMessage());
			}
		}

		@Override
		public int getBytesPerElement() {
			return 1;
		}
	}

	public static class BioFormatsUnsignedShortArrayLoader extends
		BioformatsArrayLoader implements CacheArrayLoader<VolatileShortArray>
	{

		final ByteOrder byteOrder;

		public BioFormatsUnsignedShortArrayLoader(ResourcePool<IFormatReader> readerPool, int series,
			int channel, boolean switchZandC, boolean littleEndian)
		{
			super(readerPool, series, channel, switchZandC);
			if (littleEndian) {
				byteOrder = ByteOrder.LITTLE_ENDIAN;
			}
			else {
				byteOrder = ByteOrder.BIG_ENDIAN;
			}
		}

		@Override
		public VolatileShortArray loadArray(int timepoint, int setup, int level,
			int[] dimensions, long[] min) throws InterruptedException
		{
			try {
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(series);
				reader.setResolution(level);
				int minX = (int) min[0];
				int minY = (int) min[1];
				int minZ = (int) min[2];
				int maxX = Math.min(minX + dimensions[0], reader.getSizeX());
				int maxY = Math.min(minY + dimensions[1], reader.getSizeY());
				int maxZ = Math.min(minZ + dimensions[2], reader.getSizeZ());
				int w = maxX - minX;
				int h = maxY - minY;
				int d = maxZ - minZ;
				int nElements = (w * h * d);
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 2);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = reader.openBytes(switchZandC ? reader.getIndex(channel,
						z, timepoint) : reader.getIndex(z, channel, timepoint), minX, minY,
						w, h);
					buffer.put(bytes);
				}
				readerPool.recycle(reader);
				short[] shorts = new short[nElements];
				buffer.flip();
				buffer.order(byteOrder).asShortBuffer().get(shorts);
				return new VolatileShortArray(shorts, true);
			}
			catch (Exception e) {
				throw new InterruptedException(e.getMessage());
			}
		}

		@Override
		public int getBytesPerElement() {
			return 2;
		}
	}

	public static class BioFormatsFloatArrayLoader extends BioformatsArrayLoader
		implements CacheArrayLoader<VolatileFloatArray>
	{

		final ByteOrder byteOrder;

		public BioFormatsFloatArrayLoader(ResourcePool<IFormatReader> readerPool, int series,
										  int channel, boolean switchZandC, boolean littleEndian)
		{
			super(readerPool, series, channel, switchZandC);
			if (littleEndian) {
				byteOrder = ByteOrder.LITTLE_ENDIAN;
			}
			else {
				byteOrder = ByteOrder.BIG_ENDIAN;
			}
		}

		@Override
		public VolatileFloatArray loadArray(int timepoint, int setup, int level,
			int[] dimensions, long[] min) throws InterruptedException
		{
			try {
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(series);
				reader.setResolution(level);
				int minX = (int) min[0];
				int minY = (int) min[1];
				int minZ = (int) min[2];
				int maxX = Math.min(minX + dimensions[0], reader.getSizeX());
				int maxY = Math.min(minY + dimensions[1], reader.getSizeY());
				int maxZ = Math.min(minZ + dimensions[2], reader.getSizeZ());
				int w = maxX - minX;
				int h = maxY - minY;
				int d = maxZ - minZ;
				int nElements = (w * h * d);
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 4);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = reader.openBytes(switchZandC ? reader.getIndex(channel,
						z, timepoint) : reader.getIndex(z, channel, timepoint), minX, minY,
						w, h);
					buffer.put(bytes);
				}
				readerPool.recycle(reader);
				float[] floats = new float[nElements];
				buffer.flip();
				buffer.order(byteOrder).asFloatBuffer().get(floats);
				return new VolatileFloatArray(floats, true);
			}
			catch (Exception e) {
				throw new InterruptedException(e.getMessage());
			}
		}

		@Override
		public int getBytesPerElement() {
			return 4;
		}
	}

	public static class BioFormatsRGBArrayLoader extends BioformatsArrayLoader
		implements CacheArrayLoader<VolatileIntArray>
	{

		public BioFormatsRGBArrayLoader(ResourcePool<IFormatReader> readerPool, int series,
			int channel, boolean switchZandC)
		{
			super(readerPool, series, channel, switchZandC);
		}

		// Annoying because bioformats returns 3 bytes, while imglib2 requires ARGB,
		// so 4 bytes
		@Override
		public VolatileIntArray loadArray(int timepoint, int setup, int level,
			int[] dimensions, long[] min) throws InterruptedException
		{
			try {
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(series);
				reader.setResolution(level);
				int minX = (int) min[0];
				int minY = (int) min[1];
				int minZ = (int) min[2];
				int maxX = Math.min(minX + dimensions[0], reader.getSizeX());
				int maxY = Math.min(minY + dimensions[1], reader.getSizeY());
				int maxZ = Math.min(minZ + dimensions[2], reader.getSizeZ());
				int w = maxX - minX;
				int h = maxY - minY;
				int d = maxZ - minZ;
				int nElements = (w * h * d);
				byte[] bytes;

				if (d == 1) {
					bytes = reader.openBytes(switchZandC ? reader.getIndex(channel, minZ,
						timepoint) : reader.getIndex(minZ, channel, timepoint), minX, minY,
						w, h);
				}
				else {
					int nBytesPerPlane = nElements * 3;
					bytes = new byte[nBytesPerPlane];
					int offset = 0;
					for (int z = minZ; z < maxZ; z++) {
						byte[] bytesCurrentPlane = reader.openBytes(switchZandC ? reader
							.getIndex(channel, z, timepoint) : reader.getIndex(z, channel,
								timepoint), minX, minY, w, h);
						System.arraycopy(bytesCurrentPlane, 0, bytes, offset,
							nBytesPerPlane);
						offset += nBytesPerPlane;
					}
				}
				readerPool.recycle(reader);
				int[] ints = new int[nElements];
				int idxPx = 0;
				for (int i = 0; i < nElements; i++) {
					ints[i] = ((0xff) << 24) | ((bytes[idxPx] & 0xff) << 16) |
						((bytes[idxPx + 1] & 0xff) << 8) | (bytes[idxPx + 2] & 0xff);
					idxPx += 3;
				}
				return new VolatileIntArray(ints, true);
			}
			catch (Exception e) {
				throw new InterruptedException(e.getMessage());
			}
		}

		@Override
		public int getBytesPerElement() {
			return 4;
		}
	}

	public static class BioFormatsIntArrayLoader extends BioformatsArrayLoader
		implements CacheArrayLoader<VolatileIntArray>
	{

		final ByteOrder byteOrder;

		public BioFormatsIntArrayLoader(ResourcePool<IFormatReader> readerPool, int series,
			int channel, boolean switchZandC, boolean littleEndian)
		{
			super(readerPool, series, channel, switchZandC);
			if (littleEndian) {
				byteOrder = ByteOrder.LITTLE_ENDIAN;
			}
			else {
				byteOrder = ByteOrder.BIG_ENDIAN;
			}
		}

		@Override
		public VolatileIntArray loadArray(int timepoint, int setup, int level,
			int[] dimensions, long[] min) throws InterruptedException
		{
			try {
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(series);
				reader.setResolution(level);
				int minX = (int) min[0];
				int minY = (int) min[1];
				int minZ = (int) min[2];
				int maxX = Math.min(minX + dimensions[0], reader.getSizeX());
				int maxY = Math.min(minY + dimensions[1], reader.getSizeY());
				int maxZ = Math.min(minZ + dimensions[2], reader.getSizeZ());
				int w = maxX - minX;
				int h = maxY - minY;
				int d = maxZ - minZ;
				int nElements = (w * h * d);
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 4);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = reader.openBytes(switchZandC ? reader.getIndex(channel,
						z, timepoint) : reader.getIndex(z, channel, timepoint), minX, minY,
						w, h);
					buffer.put(bytes);
				}
				readerPool.recycle(reader);
				int[] ints = new int[nElements];
				buffer.flip();
				buffer.order(byteOrder).asIntBuffer().get(ints);
				return new VolatileIntArray(ints, true);
			}
			catch (Exception e) {
				throw new InterruptedException(e.getMessage());
			}
		}

		@Override
		public int getBytesPerElement() {
			return 4;
		}
	}

}
