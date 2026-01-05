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

package ch.epfl.biop.bdv.img.pyramidize;

import bdv.img.cache.CacheArrayLoader;
import ch.epfl.biop.bdv.img.ResourcePool;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsSetupLoader;
import loci.formats.IFormatReader;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.img.basictypeaccess.DataAccess;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileIntArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.List;

// Copied from N5 Array Loader

/**
 * This class translates byte arrays given by a {@link ResourcePool}
 * of Bio-Formats {@link IFormatReader} into ImgLib2 structures
 * <p>
 * Supported pixel types:
 * - unsigned 8-bits integer
 * - signed 8-bits integer
 * - unsigned 16-bits integer
 * - signed 16-bits integer
 * - float (java sense : 32 bits float)
 * - rgb (24 bits in Bio-Formats, translated to {@link net.imglib2.type.numeric.ARGBType} 32 bits)
 * - signed 32-bits integer
 * <p>
 * See also {@link BioFormatsSetupLoader}
 *
 */
public class PyramidizeArrayLoaders {

	/**
	 * Generic class with the necessary elements to read and load pixels
	 */
	abstract static class PyramidizeArrayLoader<T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V> & NativeType<V>, A extends DataAccess & ArrayDataAccess<A>> {
		final PyramidizeSetupLoader<T,V,A> psl;

		private PyramidizeArrayLoader(PyramidizeSetupLoader<T,V,A> psl)
		{
			this.psl = psl;
		}

		abstract void init();

	}

	/**
	 * Class explaining how to read and load pixels of type : Unsigned Byte (8 bits)
	 */
	protected static class PyramidizeByteArrayLoader extends
			PyramidizeArrayLoader implements CacheArrayLoader<VolatileByteArray>
	{
		final List<List<RandomAccessibleInterval<ByteType>>> rais00 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<ByteType>>> rais01 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<ByteType>>> rais10 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<ByteType>>> rais11 = new ArrayList<>();
		protected PyramidizeByteArrayLoader(PyramidizeSetupLoader psl)
		{
			super(psl);
		}

		void init() {
			for (int tp = 0; tp<psl.opener.getNTimePoints(); tp++) {
				rais00.add(new ArrayList<>());
				rais01.add(new ArrayList<>());
				rais10.add(new ArrayList<>());
				rais11.add(new ArrayList<>());
				for (int level = 1; level<psl.numMipmapLevels(); level++) {
					RandomAccessibleInterval<ByteType> rai = Views.expandBorder(psl.getImage(tp, level-1),1,1,0);
					rais00.get(tp).add(Views.subsample(rai,2,2,1));
					rais01.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,0,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais10.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{0,1,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais11.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,1,0}, rai.dimensionsAsLongArray()),2,2,1));
				}
			}
		}

		@Override
		public VolatileByteArray loadArray(int timepoint, int setup, int level,
										   int[] dimensions, long[] min) {
			assert dimensions[2]==1;
			Interval cell = Intervals.createMinSize(min[0], min[1], min[2], dimensions[0], dimensions[1], dimensions[2]);

			final Cursor<ByteType> c00 = Views.flatIterable(Views.interval(rais00.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<ByteType> c01 = Views.flatIterable(Views.interval(rais01.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<ByteType> c10 = Views.flatIterable(Views.interval(rais10.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<ByteType> c11 = Views.flatIterable(Views.interval(rais11.get(timepoint).get(level-1), cell)).cursor();
			int nElements = (dimensions[0] * dimensions[1] * dimensions[2]);

			byte[] bytes = new byte[nElements];

			int idx = 0;
			while (c00.hasNext()) {
				bytes[idx] = (byte)
						((c00.next().get()
								+c01.next().get()
								+c10.next().get()
								+c11.next().get())/4);
				idx++;
			}
			return new VolatileByteArray(bytes, true);
		}

		@Override
		public int getBytesPerElement() {
			return 1;
		}
	}

	/**
	 * Class explaining how to read and load pixels of type : Unsigned Byte (8 bits)
	 */
	protected static class PyramidizeUnsignedByteArrayLoader extends
			PyramidizeArrayLoader implements CacheArrayLoader<VolatileByteArray>
	{
		final List<List<RandomAccessibleInterval<UnsignedByteType>>> rais00 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<UnsignedByteType>>> rais01 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<UnsignedByteType>>> rais10 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<UnsignedByteType>>> rais11 = new ArrayList<>();
		protected PyramidizeUnsignedByteArrayLoader(PyramidizeSetupLoader psl)
		{
			super(psl);
		}

		void init() {
			for (int tp = 0; tp<psl.opener.getNTimePoints(); tp++) {
				rais00.add(new ArrayList<>());
				rais01.add(new ArrayList<>());
				rais10.add(new ArrayList<>());
				rais11.add(new ArrayList<>());
				for (int level = 1; level<psl.numMipmapLevels(); level++) {
					RandomAccessibleInterval<UnsignedByteType> rai = Views.expandBorder(psl.getImage(tp, level-1),1,1,0);
					rais00.get(tp).add(Views.subsample(rai,2,2,1));
					rais01.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,0,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais10.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{0,1,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais11.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,1,0}, rai.dimensionsAsLongArray()),2,2,1));
				}
			}
		}

		@Override
		public VolatileByteArray loadArray(int timepoint, int setup, int level,
										   int[] dimensions, long[] min) {
			assert dimensions[2]==1;
			Interval cell = Intervals.createMinSize(min[0], min[1], min[2], dimensions[0], dimensions[1], dimensions[2]);

			final Cursor<UnsignedByteType> c00 = Views.flatIterable(Views.interval(rais00.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<UnsignedByteType> c01 = Views.flatIterable(Views.interval(rais01.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<UnsignedByteType> c10 = Views.flatIterable(Views.interval(rais10.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<UnsignedByteType> c11 = Views.flatIterable(Views.interval(rais11.get(timepoint).get(level-1), cell)).cursor();
			int nElements = (dimensions[0] * dimensions[1] * dimensions[2]);

			byte[] bytes = new byte[nElements];

			int idx = 0;
			while (c00.hasNext()) {
				bytes[idx] = (byte)
						((c00.next().get()
								+c01.next().get()
								+c10.next().get()
								+c11.next().get())/4);
				idx++;
			}
			return new VolatileByteArray(bytes, true);
		}

		@Override
		public int getBytesPerElement() {
			return 1;
		}
	}

	/**
	 * Class explaining how to read and load pixels of type : unsigned short (16 bits)
	 */
	protected static class PyramidizeShortArrayLoader extends
			PyramidizeArrayLoader implements CacheArrayLoader<VolatileShortArray>
	{
		final List<List<RandomAccessibleInterval<ShortType>>> rais00 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<ShortType>>> rais01 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<ShortType>>> rais10 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<ShortType>>> rais11 = new ArrayList<>();

		protected PyramidizeShortArrayLoader(PyramidizeSetupLoader psl)	{super(psl);}

		void init() {
			for (int tp = 0; tp<psl.opener.getNTimePoints(); tp++) {
				rais00.add(new ArrayList<>());
				rais01.add(new ArrayList<>());
				rais10.add(new ArrayList<>());
				rais11.add(new ArrayList<>());
				for (int level = 1; level<psl.numMipmapLevels(); level++) {
					RandomAccessibleInterval<ShortType> rai = Views.expandBorder(psl.getImage(tp, level-1),1,1,0);
					rais00.get(tp).add(Views.subsample(rai,2,2,1));
					rais01.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,0,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais10.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{0,1,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais11.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,1,0}, rai.dimensionsAsLongArray()),2,2,1));
				}
			}
		}

		@Override
		public VolatileShortArray loadArray(int timepoint, int setup, int level,
											int[] dimensions, long[] min) {
			assert dimensions[2]==1;
			Interval cell = Intervals.createMinSize(min[0], min[1], min[2], dimensions[0], dimensions[1], dimensions[2]);

			final Cursor<ShortType> c00 = Views.flatIterable(Views.interval(rais00.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<ShortType> c01 = Views.flatIterable(Views.interval(rais01.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<ShortType> c10 = Views.flatIterable(Views.interval(rais10.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<ShortType> c11 = Views.flatIterable(Views.interval(rais11.get(timepoint).get(level-1), cell)).cursor();
			int nElements = (dimensions[0] * dimensions[1] * dimensions[2]);

			short[] shorts = new short[nElements*2];

			int idx = 0;
			while (c00.hasNext()) {
				shorts[idx] = (short)
						((c00.next().get()
								+c01.next().get()
								+c10.next().get()
								+c11.next().get())/4);
				idx++;
			}
			return new VolatileShortArray(shorts, true);
		}

		@Override
		public int getBytesPerElement() {
			return 2;
		}
	}

	/**
	 * Class explaining how to read and load pixels of type : unsigned short (16 bits)
	 */
	protected static class PyramidizeUnsignedShortArrayLoader extends
			PyramidizeArrayLoader implements CacheArrayLoader<VolatileShortArray>
	{
		final List<List<RandomAccessibleInterval<UnsignedShortType>>> rais00 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<UnsignedShortType>>> rais01 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<UnsignedShortType>>> rais10 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<UnsignedShortType>>> rais11 = new ArrayList<>();

		protected PyramidizeUnsignedShortArrayLoader(PyramidizeSetupLoader psl)	{super(psl);}

		void init() {
			for (int tp = 0; tp<psl.opener.getNTimePoints(); tp++) {
				rais00.add(new ArrayList<>());
				rais01.add(new ArrayList<>());
				rais10.add(new ArrayList<>());
				rais11.add(new ArrayList<>());
				for (int level = 1; level<psl.numMipmapLevels(); level++) {
					RandomAccessibleInterval<UnsignedShortType> rai = Views.expandBorder(psl.getImage(tp, level-1),1,1,0);
					rais00.get(tp).add(Views.subsample(rai,2,2,1));
					rais01.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,0,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais10.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{0,1,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais11.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,1,0}, rai.dimensionsAsLongArray()),2,2,1));
				}
			}
		}

		@Override
		public VolatileShortArray loadArray(int timepoint, int setup, int level,
										   int[] dimensions, long[] min) {
			assert dimensions[2]==1;
			Interval cell = Intervals.createMinSize(min[0], min[1], min[2], dimensions[0], dimensions[1], dimensions[2]);

			final Cursor<UnsignedShortType> c00 = Views.flatIterable(Views.interval(rais00.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<UnsignedShortType> c01 = Views.flatIterable(Views.interval(rais01.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<UnsignedShortType> c10 = Views.flatIterable(Views.interval(rais10.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<UnsignedShortType> c11 = Views.flatIterable(Views.interval(rais11.get(timepoint).get(level-1), cell)).cursor();
			int nElements = (dimensions[0] * dimensions[1] * dimensions[2]);

			short[] shorts = new short[nElements*2];

			int idx = 0;
			while (c00.hasNext()) {
				shorts[idx] = (short)
						((c00.next().get()
						+c01.next().get()
						+c10.next().get()
						+c11.next().get())/4);
				idx++;
			}
			return new VolatileShortArray(shorts, true);
		}

		@Override
		public int getBytesPerElement() {
			return 2;
		}
	}

	protected static class PyramidizeFloatArrayLoader extends PyramidizeArrayLoader implements CacheArrayLoader<VolatileFloatArray> {
		final List<List<RandomAccessibleInterval<FloatType>>> rais00 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<FloatType>>> rais01 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<FloatType>>> rais10 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<FloatType>>> rais11 = new ArrayList<>();
		protected PyramidizeFloatArrayLoader(PyramidizeSetupLoader psl)
		{
			super(psl);
		}

		void init() {
			for (int tp = 0; tp<psl.opener.getNTimePoints(); tp++) {
				rais00.add(new ArrayList<>());
				rais01.add(new ArrayList<>());
				rais10.add(new ArrayList<>());
				rais11.add(new ArrayList<>());
				for (int level = 1; level<psl.numMipmapLevels(); level++) {
					RandomAccessibleInterval<FloatType> rai = Views.expandBorder(psl.getImage(tp, level-1),1,1,0);
					rais00.get(tp).add(Views.subsample(rai,2,2,1));
					rais01.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,0,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais10.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{0,1,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais11.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,1,0}, rai.dimensionsAsLongArray()),2,2,1));
				}
			}
		}

		@Override
		public VolatileFloatArray loadArray(int timepoint, int setup, int level,
										   int[] dimensions, long[] min) {
			assert dimensions[2]==1;
			Interval cell = Intervals.createMinSize(min[0], min[1], min[2], dimensions[0], dimensions[1], dimensions[2]);

			final Cursor<FloatType> c00 = Views.flatIterable(Views.interval(rais00.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<FloatType> c01 = Views.flatIterable(Views.interval(rais01.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<FloatType> c10 = Views.flatIterable(Views.interval(rais10.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<FloatType> c11 = Views.flatIterable(Views.interval(rais11.get(timepoint).get(level-1), cell)).cursor();
			int nElements = (dimensions[0] * dimensions[1] * dimensions[2]);

			float[] floats = new float[nElements];

			int idx = 0;
			while (c00.hasNext()) {
				floats[idx] =
						((c00.next().get()
								+c01.next().get()
								+c10.next().get()
								+c11.next().get())/4.0f);
				idx++;
			}
			return new VolatileFloatArray(floats, true);
		}

		@Override
		public int getBytesPerElement() {
			return 4;
		}
	}

	protected static class PyramidizeIntArrayLoader extends PyramidizeArrayLoader implements CacheArrayLoader<VolatileIntArray> {
		final List<List<RandomAccessibleInterval<IntType>>> rais00 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<IntType>>> rais01 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<IntType>>> rais10 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<IntType>>> rais11 = new ArrayList<>();
		protected PyramidizeIntArrayLoader(PyramidizeSetupLoader psl)
		{
			super(psl);
		}

		void init() {
			for (int tp = 0; tp<psl.opener.getNTimePoints(); tp++) {
				rais00.add(new ArrayList<>());
				rais01.add(new ArrayList<>());
				rais10.add(new ArrayList<>());
				rais11.add(new ArrayList<>());
				for (int level = 1; level<psl.numMipmapLevels(); level++) {
					RandomAccessibleInterval<IntType> rai = Views.expandBorder(psl.getImage(tp, level-1),1,1,0);
					rais00.get(tp).add(Views.subsample(rai,2,2,1));
					rais01.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,0,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais10.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{0,1,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais11.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,1,0}, rai.dimensionsAsLongArray()),2,2,1));
				}
			}
		}

		@Override
		public VolatileIntArray loadArray(int timepoint, int setup, int level,
											int[] dimensions, long[] min) {
			assert dimensions[2]==1;
			Interval cell = Intervals.createMinSize(min[0], min[1], min[2], dimensions[0], dimensions[1], dimensions[2]);

			final Cursor<IntType> c00 = Views.flatIterable(Views.interval(rais00.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<IntType> c01 = Views.flatIterable(Views.interval(rais01.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<IntType> c10 = Views.flatIterable(Views.interval(rais10.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<IntType> c11 = Views.flatIterable(Views.interval(rais11.get(timepoint).get(level-1), cell)).cursor();
			int nElements = (dimensions[0] * dimensions[1] * dimensions[2]);

			int[] ints = new int[nElements];

			int idx = 0;
			while (c00.hasNext()) {
				ints[idx] = (c00.next().get()
								+c01.next().get()
								+c10.next().get()
								+c11.next().get())/4;
				idx++;
			}
			return new VolatileIntArray(ints, true);
		}

		@Override
		public int getBytesPerElement() {
			return 4;
		}
	}

	protected static class PyramidizeARGBArrayLoader extends PyramidizeArrayLoader implements CacheArrayLoader<VolatileIntArray> {
		final List<List<RandomAccessibleInterval<ARGBType>>> rais00 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<ARGBType>>> rais01 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<ARGBType>>> rais10 = new ArrayList<>();
		final List<List<RandomAccessibleInterval<ARGBType>>> rais11 = new ArrayList<>();
		protected PyramidizeARGBArrayLoader(PyramidizeSetupLoader psl)
		{
			super(psl);
		}

		void init() {
			for (int tp = 0; tp<psl.opener.getNTimePoints(); tp++) {
				rais00.add(new ArrayList<>());
				rais01.add(new ArrayList<>());
				rais10.add(new ArrayList<>());
				rais11.add(new ArrayList<>());
				for (int level = 1; level<psl.numMipmapLevels(); level++) {
					RandomAccessibleInterval<ARGBType> rai = Views.expandBorder(psl.getImage(tp, level-1),1,1,0);
					rais00.get(tp).add(Views.subsample(rai,2,2,1));
					rais01.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,0,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais10.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{0,1,0}, rai.dimensionsAsLongArray()),2,2,1));
					rais11.get(tp).add(Views.subsample(Views.offsetInterval(rai, new long[]{1,1,0}, rai.dimensionsAsLongArray()),2,2,1));
				}
			}
		}

		@Override
		public VolatileIntArray loadArray(int timepoint, int setup, int level,
										  int[] dimensions, long[] min) {
			assert dimensions[2]==1;
			Interval cell = Intervals.createMinSize(min[0], min[1], min[2], dimensions[0], dimensions[1], dimensions[2]);

			final Cursor<ARGBType> c00 = Views.flatIterable(Views.interval(rais00.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<ARGBType> c01 = Views.flatIterable(Views.interval(rais01.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<ARGBType> c10 = Views.flatIterable(Views.interval(rais10.get(timepoint).get(level-1), cell)).cursor();
			final Cursor<ARGBType> c11 = Views.flatIterable(Views.interval(rais11.get(timepoint).get(level-1), cell)).cursor();
			int nElements = (dimensions[0] * dimensions[1] * dimensions[2]);

			int[] ints = new int[nElements];

			int idx = 0;
			while (c00.hasNext()) {
				// This looks expensive... not sure if there's a better way

				int v00 = c00.next().get();
				int v01 = c01.next().get();
				int v10 = c10.next().get();
				int v11 = c11.next().get();

				ints[idx] = ARGBType.rgba(
						(ARGBType.red(v00)+ARGBType.red(v10)+ARGBType.red(v01)+ARGBType.red(v11))/4, //red
						(ARGBType.green(v00)+ARGBType.green(v10)+ARGBType.green(v01)+ARGBType.green(v11))/4, //g
						(ARGBType.blue(v00)+ARGBType.blue(v10)+ARGBType.blue(v01)+ARGBType.blue(v11))/4, //b
						(ARGBType.alpha(v00)+ARGBType.alpha(v10)+ARGBType.alpha(v01)+ARGBType.alpha(v11))/4 //a
				);
				idx++;
			}
			return new VolatileIntArray(ints, true);
		}

		@Override
		public int getBytesPerElement() {
			return 4;
		}
	}
}
