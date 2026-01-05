/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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

package ch.epfl.biop.bdv.img.omero;

import bdv.img.cache.CacheArrayLoader;
import ch.epfl.biop.bdv.img.ResourcePool;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import omero.api.RawPixelsStorePrx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// Copied from N5 Array Loader

/**
 * This class translates byte arrays given by a {@link ResourcePool}
 * of OMERO {@link RawPixelsStorePrx} into ImgLib2 structures
 * <p>
 *
 * See also {@link OmeroSetupLoader}
 *
 */
public class OmeroArrayLoaders {

	/**
	 * Generic class with the necessary elements to read and load pixels
	 */
	abstract static class OmeroArrayLoader {

		final protected ResourcePool<RawPixelsStorePrx> pixelStorePool;
		final protected int channel;
		final protected int nResolutionLevels;
		final protected int sx, sy, sz;

		public OmeroArrayLoader(ResourcePool<RawPixelsStorePrx> pixelStorePool, int channel,
			int nResolutionLevels, int sx, int sy, int sz)
		{
			this.pixelStorePool = pixelStorePool;
			this.channel = channel;
			this.nResolutionLevels = nResolutionLevels;
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
		}

	}

	/**
	 * Class explaining how to read and load pixels of type : Byte (8 bits)
	 */
	public static class OmeroUnsignedByteArrayLoader extends OmeroArrayLoader
		implements CacheArrayLoader<VolatileByteArray>
	{

		public OmeroUnsignedByteArrayLoader(ResourcePool<RawPixelsStorePrx> pixelStorePool,
			int channel, int nResolutionLevels, int sx, int sy, int sz)
		{
			super(pixelStorePool, channel, nResolutionLevels, sx, sy, sz);
		}

		@Override
		public VolatileByteArray loadArray(int timepoint, int setup, int level,
										   int[] dimensions, long[] min) throws InterruptedException
		{
			try {
				// get the reader
				RawPixelsStorePrx rawPixStore = pixelStorePool.acquire();
				rawPixStore.setResolutionLevel(nResolutionLevels - 1 - level);
				int minX = (int) min[0];
				int minY = (int) min[1];
				int minZ = (int) min[2];
				int maxX = Math.min(minX + dimensions[0], sx);
				int maxY = Math.min(minY + dimensions[1], sy);
				int maxZ = Math.min(minZ + dimensions[2], sz);
				int w = maxX - minX;
				int h = maxY - minY;
				int d = maxZ - minZ;
				int nElements = (w * h * d);

				// read pixels
				ByteBuffer buffer = ByteBuffer.allocate(nElements);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytesCurrentPlane = rawPixStore.getTile(z, channel, timepoint, minX, minY,
							w, h);
					buffer.put(bytesCurrentPlane);
				}

				// release the reader
				pixelStorePool.recycle(rawPixStore);

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
	public static class OmeroUnsignedShortArrayLoader extends OmeroArrayLoader
		implements CacheArrayLoader<VolatileShortArray>
	{

		final ByteOrder byteOrder;

		public OmeroUnsignedShortArrayLoader(ResourcePool<RawPixelsStorePrx> pixelStorePool,
			int channel, int nResolutionLevels, int sx, int sy, int sz,
			boolean littleEndian)
		{

			super(pixelStorePool, channel, nResolutionLevels, sx, sy, sz);

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
				RawPixelsStorePrx rawPixStore = pixelStorePool.acquire();
				rawPixStore.setResolutionLevel(nResolutionLevels - 1 - level);
				int minX = (int) min[0];
				int minY = (int) min[1];
				int minZ = (int) min[2];
				int maxX = Math.min(minX + dimensions[0], sx);
				int maxY = Math.min(minY + dimensions[1], sy);
				int maxZ = Math.min(minZ + dimensions[2], sz);
				int w = maxX - minX;
				int h = maxY - minY;
				int d = maxZ - minZ;
				int nElements = (w * h * d);

				// read pixels
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 2);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = rawPixStore.getTile(z, channel, timepoint, minX, minY,
						w, h);
					buffer.put(bytes);
				}

				// release the reader
				pixelStorePool.recycle(rawPixStore);

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
	public static class OmeroFloatArrayLoader extends OmeroArrayLoader implements
		CacheArrayLoader<VolatileFloatArray>
	{

		final ByteOrder byteOrder;

		public OmeroFloatArrayLoader(ResourcePool<RawPixelsStorePrx> pixelStorePool, int channel,
									 int nResolutionLevels, int sx, int sy, int sz, boolean littleEndian)
		{
			super(pixelStorePool, channel, nResolutionLevels, sx, sy, sz);
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
				RawPixelsStorePrx rawPixStore = pixelStorePool.acquire();
				rawPixStore.setResolutionLevel(nResolutionLevels - 1 - level);
				int minX = (int) min[0];
				int minY = (int) min[1];
				int minZ = (int) min[2];
				int maxX = Math.min(minX + dimensions[0], sx);
				int maxY = Math.min(minY + dimensions[1], sy);
				int maxZ = Math.min(minZ + dimensions[2], sz);
				int w = maxX - minX;
				int h = maxY - minY;
				int d = maxZ - minZ;
				int nElements = (w * h * d);

				// read pixels
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 4);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = rawPixStore.getTile(z, channel, timepoint, minX, minY,
						w, h);
					buffer.put(bytes);
				}

				// release the reader
				pixelStorePool.recycle(rawPixStore);

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
	public static class OmeroRGBArrayLoader extends OmeroArrayLoader implements
		CacheArrayLoader<VolatileIntArray>
	{

		public OmeroRGBArrayLoader(ResourcePool<RawPixelsStorePrx> pixelStorePool, int channel,
			int nResolutionLevels, int sx, int sy, int sz)
		{
			super(pixelStorePool, channel, nResolutionLevels, sx, sy, sz);
		}

		@Override
		public VolatileIntArray loadArray(int timepoint, int setup, int level,
										  int[] dimensions, long[] min)
		{
			throw new IllegalStateException("OMERO is not really supposed to give RGB images. But apparently that's the case. Please reach out to the developers to fix this!");
		}

		@Override
		public int getBytesPerElement() {
			return 4;
		}
	}

	/**
	 * Class explaining how to read and load pixels of type : int (16 bits)
	 */
	public static class OmeroIntArrayLoader extends OmeroArrayLoader implements
		CacheArrayLoader<VolatileIntArray>
	{

		final ByteOrder byteOrder;

		public OmeroIntArrayLoader(ResourcePool<RawPixelsStorePrx> pixelStorePool, int channel,
			int nResolutionLevels, int sx, int sy, int sz, boolean littleEndian)
		{
			super(pixelStorePool, channel, nResolutionLevels, sx, sy, sz);
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
				RawPixelsStorePrx rawPixStore = pixelStorePool.acquire();
				rawPixStore.setResolutionLevel(nResolutionLevels - 1 - level);
				int minX = (int) min[0];
				int minY = (int) min[1];
				int minZ = (int) min[2];
				int maxX = Math.min(minX + dimensions[0], sx);
				int maxY = Math.min(minY + dimensions[1], sy);
				int maxZ = Math.min(minZ + dimensions[2], sz);
				int w = maxX - minX;
				int h = maxY - minY;
				int d = maxZ - minZ;
				int nElements = (w * h * d);

				// read pixels
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 4);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = rawPixStore.getTile(z, channel, timepoint, minX, minY,
						w, h);
					buffer.put(bytes);
				}

				// release the reader
				pixelStorePool.recycle(rawPixStore);

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
