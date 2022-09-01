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

package ch.epfl.biop.bdv.img.omero;

import bdv.img.cache.CacheArrayLoader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import omero.api.RawPixelsStorePrx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// Copied from N5 Array Loader
public class OmeroArrayLoaders {

	abstract static class OmeroArrayLoader {

		final protected RawPixelsStorePool pixelStorePool;
		final protected int channel;
		final protected int nResolutionLevels;
		final protected int sx, sy, sz;

		public OmeroArrayLoader(RawPixelsStorePool pixelStorePool, int channel,
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

	public static class OmeroUnsignedByteArrayLoader extends OmeroArrayLoader
		implements CacheArrayLoader<VolatileByteArray>
	{

		public OmeroUnsignedByteArrayLoader(RawPixelsStorePool pixelStorePool,
			int channel, int nResolutionLevels, int sx, int sy, int sz)
		{
			super(pixelStorePool, channel, nResolutionLevels, sx, sy, sz);
		}

		@Override
		public VolatileByteArray loadArray(int timepoint, int setup, int level,
			int[] dimensions, long[] min) throws InterruptedException
		{
			try {
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
				if (dimensions[2] == 1) {
					// Optimisation (maybe useful ? should avoid an array allocation and
					// the ByteBuffer overhead
					byte[] bytes = rawPixStore.getTile(minZ, channel, timepoint, minX,
						minY, w, h);
					pixelStorePool.recycle(rawPixStore);
					return new VolatileByteArray(bytes, true);
				}
				else {
					byte[] bytes = new byte[nElements];
					int offset = 0;
					for (int z = minZ; z < maxZ; z++) {
						byte[] bytesCurrentPlane = rawPixStore.getTile(z, channel,
							timepoint, minX, minY, w, h);
						System.arraycopy(bytesCurrentPlane, 0, bytes, offset, nElements);
						offset += nElements;
					}
					pixelStorePool.recycle(rawPixStore);
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

	public static class OmeroUnsignedShortArrayLoader extends OmeroArrayLoader
		implements CacheArrayLoader<VolatileShortArray>
	{

		final ByteOrder byteOrder;

		public OmeroUnsignedShortArrayLoader(RawPixelsStorePool pixelStorePool,
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
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 2);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = rawPixStore.getTile(z, channel, timepoint, minX, minY,
						w, h);
					buffer.put(bytes);
				}
				pixelStorePool.recycle(rawPixStore);
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

	public static class OmeroFloatArrayLoader extends OmeroArrayLoader implements
		CacheArrayLoader<VolatileFloatArray>
	{

		final ByteOrder byteOrder;

		public OmeroFloatArrayLoader(RawPixelsStorePool pixelStorePool, int channel,
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
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 4);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = rawPixStore.getTile(z, channel, timepoint, minX, minY,
						w, h);
					buffer.put(bytes);
				}
				pixelStorePool.recycle(rawPixStore);
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

	public static class OmeroRGBArrayLoader extends OmeroArrayLoader implements
		CacheArrayLoader<VolatileIntArray>
	{

		public OmeroRGBArrayLoader(RawPixelsStorePool pixelStorePool, int channel,
			int nResolutionLevels, int sx, int sy, int sz)
		{
			super(pixelStorePool, channel, nResolutionLevels, sx, sy, sz);
		}

		// Annoying because bioformats returns 3 bytes, while imglib2 requires ARGB,
		// so 4 bytes
		@Override
		public VolatileIntArray loadArray(int timepoint, int setup, int level,
			int[] dimensions, long[] min) throws InterruptedException
		{
			try {
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
				byte[] bytes;

				if (d == 1) {
					bytes = rawPixStore.getTile(minZ, channel, timepoint, minX, minY, w,
						h);
				}
				else {
					int nBytesPerPlane = nElements * 3;
					bytes = new byte[nBytesPerPlane];
					int offset = 0;
					for (int z = minZ; z < maxZ; z++) {
						byte[] bytesCurrentPlane = rawPixStore.getTile(z, channel,
							timepoint, minX, minY, w, h);
						System.arraycopy(bytesCurrentPlane, 0, bytes, offset,
							nBytesPerPlane);
						offset += nBytesPerPlane;
					}
				}
				pixelStorePool.recycle(rawPixStore);
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

	public static class OmeroIntArrayLoader extends OmeroArrayLoader implements
		CacheArrayLoader<VolatileIntArray>
	{

		final ByteOrder byteOrder;

		public OmeroIntArrayLoader(RawPixelsStorePool pixelStorePool, int channel,
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
				ByteBuffer buffer = ByteBuffer.allocate(nElements * 4);
				for (int z = minZ; z < maxZ; z++) {
					byte[] bytes = rawPixStore.getTile(z, channel, timepoint, minX, minY,
						w, h);
					buffer.put(bytes);
				}
				pixelStorePool.recycle(rawPixStore);
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
