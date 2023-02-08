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

package ch.epfl.biop.bdv.img.bioformats.entity;

import mpicbg.spim.data.generic.base.Entity;

/**
 * {@link Entity} that stores the index of the series within Bio-Formats
 * Complementary with {@link FileName}
 */

public class SeriesIndex extends Entity implements
	Comparable<SeriesIndex>
{
	public SeriesIndex(int indexSeries) {
		super(indexSeries);
	}
	/**
	 * Compares the {@link #getId() ids}.
	 */
	@Override
	public int compareTo(final SeriesIndex o) {
		return getId() - o.getId();
	}

	protected SeriesIndex() {}
}
