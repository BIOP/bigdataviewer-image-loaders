/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2023 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

/**
 * This class translates byte arrays given by a {@link ResourcePool}
 * of Bio-Formats {@link IFormatReader} into ImgLib2 structures
 * <p>
 * Supported pixel types:
 * - unsigned 8-bits integer
 * - unsigned 16-bits integer
 * - float (java sense : 32 bits float)
 * - rgb (24 bits in Bio-Formats, translated to {@link net.imglib2.type.numeric.ARGBType} 32 bits)
 * - signed 32-bits integer
 * <p>
 * See also {@link BioFormatsSetupLoader}
 *
 */
public class BioFormatsArrayLoaders {

	/**
	 * Generic class with the necessary elements to read and load pixels
	 */
	abstract static class BioformatsArrayLoader {

		final protected ResourcePool<IFormatReader> readerPool;
		final protected int channel;
		final protected int iSeries;

		private BioformatsArrayLoader(ResourcePool<IFormatReader> readerPool, int channel, int iSeries)
		{
			this.readerPool = readerPool;
			this.channel = channel;
			this.iSeries = iSeries;
		}

	}

	/**
	 * Class explaining how to read and load pixels of type : Unsigned Byte (8 bits)
	 */
	protected static class BioFormatsUnsignedByteArrayLoader extends
		BioformatsArrayLoader implements CacheArrayLoader<VolatileByteArray>
	{

		protected BioFormatsUnsignedByteArrayLoader(ResourcePool<IFormatReader> readerPool,
			int channel, int iSeries)
		{
			super(readerPool, channel, iSeries);
		}

		@Override
		public VolatileByteArray loadArray(int timepoint, int setup, int level,
										   int[] dimensions, long[] min) throws InterruptedException
		{
			try {
				// get the reader
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(iSeries);
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

				// read pixels
				ByteBuffer buffer = ByteBuffer.allocate(nElements);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytesCurrentPlane = reader.openBytes(reader.getIndex(z, channel,
							timepoint), minX, minY, w, h);
					buffer.put(bytesCurrentPlane);
				}

				// release the reader
				readerPool.recycle(reader);
				return new VolatileByteArray(buffer.array(), true);
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

	/**
	 * Class explaining how to read and load pixels of type : unsigned short (16 bits)
	 */
	public static class BioFormatsUnsignedShortArrayLoader extends
		BioformatsArrayLoader implements CacheArrayLoader<VolatileShortArray>
	{

		final ByteOrder byteOrder;

		protected BioFormatsUnsignedShortArrayLoader(ResourcePool<IFormatReader> readerPool,
			int channel, int iSeries, boolean littleEndian)
		{
			super(readerPool, channel, iSeries);
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
				// get the reader
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(iSeries);
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

				// read pixels
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 2);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = reader.openBytes(reader.getIndex(z, channel, timepoint), minX, minY,
						w, h);
					buffer.put(bytes);
				}

				// release the reader
				readerPool.recycle(reader);

				// unsigned short specific transform
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

	/**
	 * Class explaining how to read and load pixels of type : float (32 bits)
	 */
	public static class BioFormatsFloatArrayLoader extends BioformatsArrayLoader
		implements CacheArrayLoader<VolatileFloatArray>
	{

		final ByteOrder byteOrder;

		protected BioFormatsFloatArrayLoader(ResourcePool<IFormatReader> readerPool,
										  int channel, int iSeries, boolean littleEndian)
		{
			super(readerPool, channel, iSeries);
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
				// get the reader
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(iSeries);
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

				// read pixels
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 4);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = reader.openBytes(reader.getIndex(z, channel, timepoint), minX, minY,
						w, h);
					buffer.put(bytes);
				}

				// release the reader
				readerPool.recycle(reader);

				// float specific transform
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

	/**
	 * Class explaining how to read and load pixels of type : RGB (3 * 8 bits)
	 */
	public static class BioFormatsRGBArrayLoader extends BioformatsArrayLoader
		implements CacheArrayLoader<VolatileIntArray>
	{

		protected BioFormatsRGBArrayLoader(ResourcePool<IFormatReader> readerPool,
			int channel, int iSeries)
		{
			super(readerPool, channel, iSeries);
		}

		// Annoying because bioformats returns 3 bytes, while imglib2 requires ARGB,
		// so 4 bytes
		@Override
		public VolatileIntArray loadArray(int timepoint, int setup, int level,
										  int[] dimensions, long[] min) throws InterruptedException
		{
			try {
				// get the reader
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(iSeries);
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

				// read pixels
				byte[] bytes;
				if (d == 1) {
					bytes = reader.openBytes(reader.getIndex(minZ, channel, timepoint), minX, minY,
							w, h);
				}
				else {
					int nBytesPerPlane = nElements * 3;
					bytes = new byte[nBytesPerPlane];
					int offset = 0;
					for (int z = minZ; z < maxZ; z++) {
						byte[] bytesCurrentPlane = reader.openBytes(reader.getIndex(z, channel,
								timepoint), minX, minY, w, h);
						System.arraycopy(bytesCurrentPlane, 0, bytes, offset,
								nBytesPerPlane);
						offset += nBytesPerPlane;
					}
				}
				boolean interleaved = reader.isInterleaved();

				// release the reader
				readerPool.recycle(reader);

				// RGB specific transform
				int[] ints = new int[nElements];
				int idxPx = 0;
				if (interleaved) {
					for (int i = 0; i < nElements; i++) {
						ints[i] = ((0xff) << 24) | ((bytes[idxPx] & 0xff) << 16) |
								((bytes[idxPx + 1] & 0xff) << 8) | (bytes[idxPx + 2] & 0xff);
						idxPx += 3;
					}
				} else {
					int bOffset = 2*nElements;
					for (int i = 0; i < nElements; i++) {
						ints[i] = ((bytes[idxPx] & 0xff) << 16 ) | ((bytes[idxPx+nElements] & 0xff) << 8) | (bytes[idxPx+bOffset] & 0xff);
						idxPx += 1;
					}
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

	/**
	 * Class explaining how to read and load pixels of type : signed int (32 bits)
	 */
	public static class BioFormatsIntArrayLoader extends BioformatsArrayLoader
		implements CacheArrayLoader<VolatileIntArray>
	{

		final ByteOrder byteOrder;

		protected BioFormatsIntArrayLoader(ResourcePool<IFormatReader> readerPool,
										int channel, int iSeries, boolean littleEndian)
		{
			super(readerPool, channel, iSeries);
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
				// get the reader
				IFormatReader reader = readerPool.acquire();
				reader.setSeries(iSeries);
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

				// read pixels
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 4);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = reader.openBytes(reader.getIndex(z, channel, timepoint), minX, minY,
						w, h);
					buffer.put(bytes);
				}

				// release the reader
				readerPool.recycle(reader);

				// int specific transform
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
