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

package ch.epfl.biop.bdv.img.omero;

import bdv.img.cache.CacheArrayLoader;
import ch.epfl.biop.bdv.img.bioformats.ReaderPool;
import loci.formats.IFormatReader;
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

		public OmeroArrayLoader(RawPixelsStorePool pixelStorePool, int channel, int nResolutionLevels, int sx, int sy, int sz)
		{
			this.pixelStorePool = pixelStorePool;
			this.channel = channel;
			this.nResolutionLevels = nResolutionLevels;
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
		}

	}

	public static class OmeroUnsignedByteArrayLoader extends
			OmeroArrayLoader implements CacheArrayLoader<VolatileByteArray>
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
				rawPixStore.setResolutionLevel(nResolutionLevels-1-level);
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
					byte[] bytes = rawPixStore.getTile(minZ, channel, timepoint, minX, minY, w, h);
					pixelStorePool.recycle(rawPixStore);
					return new VolatileByteArray(bytes, true);
				}
				else {
					byte[] bytes = new byte[nElements];
					int offset = 0;
					for (int z = minZ; z < maxZ; z++) {
						byte[] bytesCurrentPlane = rawPixStore.getTile(z, channel, timepoint, minX, minY, w, h);
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

/*
	public static class BioFormatsUnsignedShortArrayLoader extends
			OmeroArrayLoader implements CacheArrayLoader<VolatileShortArray>
	{

		final ByteOrder byteOrder;

		public BioFormatsUnsignedShortArrayLoader(ReaderPool readerPool, int series,
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
				IFormatReader reader = pixelStorePool.acquire();
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
				pixelStorePool.recycle(reader);
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

	public static class BioFormatsFloatArrayLoader extends OmeroArrayLoader
		implements CacheArrayLoader<VolatileFloatArray>
	{

		final ByteOrder byteOrder;

		public BioFormatsFloatArrayLoader(ReaderPool readerPool, int series,
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

				IFormatReader reader = pixelStorePool.acquire();
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
				pixelStorePool.recycle(reader);
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

	public static class BioFormatsRGBArrayLoader extends OmeroArrayLoader
		implements CacheArrayLoader<VolatileIntArray>
	{

		public BioFormatsRGBArrayLoader(ReaderPool readerPool, int series,
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
				IFormatReader reader = pixelStorePool.acquire();
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
				pixelStorePool.recycle(reader);
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

	public static class BioFormatsIntArrayLoader extends OmeroArrayLoader
		implements CacheArrayLoader<VolatileIntArray>
	{

		final ByteOrder byteOrder;

		public BioFormatsIntArrayLoader(ReaderPool readerPool, int series,
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
				IFormatReader reader = pixelStorePool.acquire();
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
				pixelStorePool.recycle(reader);
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
*/
}
