/*-
 * #%L
 * Repo containing extra settings that can be stored in spimdata file format
 * %%
 * Copyright (C) 2020 - 2022 EPFL
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

package ch.epfl.biop.bdv.img.imageplus;

import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Sampler;
import net.imglib2.type.numeric.NumericType;

public class ZerosRAI<T extends NumericType<T>> implements
	RandomAccessibleInterval<T>
{

	final T t;

	final long[] dimensions;

	final RandomAccess<T> zerosRandomAccess;

	public ZerosRAI(T typeInstance, long[] dimensions) {
		this.t = typeInstance;
		t.setZero();
		this.dimensions = dimensions;
		this.zerosRandomAccess = new ZerosRandomAccess();
	}

	@Override
	public long min(int d) {
		return 0;
	}

	@Override
	public long max(int d) {
		return dimensions[d];
	}

	@Override
	public RandomAccess<T> randomAccess() {
		return zerosRandomAccess;
	}

	@Override
	public RandomAccess<T> randomAccess(Interval interval) {
		return zerosRandomAccess;
	}

	@Override
	public int numDimensions() {
		return dimensions.length;
	}

	public class ZerosRandomAccess implements RandomAccess<T> {

		@Override
		public RandomAccess<T> copyRandomAccess() {
			return new ZerosRandomAccess();
		}

		@Override
		public long getLongPosition(int d) {
			return d;
		}

		@Override
		public void fwd(int d) {

		}

		@Override
		public void bck(int d) {

		}

		@Override
		public void move(int distance, int d) {

		}

		@Override
		public void move(long distance, int d) {

		}

		@Override
		public void move(Localizable distance) {

		}

		@Override
		public void move(int[] distance) {

		}

		@Override
		public void move(long[] distance) {

		}

		@Override
		public void setPosition(Localizable position) {

		}

		@Override
		public void setPosition(int[] position) {

		}

		@Override
		public void setPosition(long[] position) {

		}

		@Override
		public void setPosition(int position, int d) {

		}

		@Override
		public void setPosition(long position, int d) {

		}

		@Override
		public int numDimensions() {
			return dimensions.length;
		}

		@Override
		public T get() {
			return t;
		}

		@Override
		public Sampler<T> copy() {
			return new Sampler<T>() {

				@Override
				public T get() {
					return t;
				}

				@Override
				public Sampler<T> copy() {
					return this;
				}
			};
		}
	}
}
