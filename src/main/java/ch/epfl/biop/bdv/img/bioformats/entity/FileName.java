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

package ch.epfl.biop.bdv.img.bioformats.entity;

import mpicbg.spim.data.generic.base.NamedEntity;

/**
 * Just storing the filename of the series within - does not store the path
 * because it is hard to update the path within an {@link NamedEntity}
 * and it's probably not what it had been designed for.
 * <p>
 * Note : you can have multiple files in a dataset backed by a
 * {@link ch.epfl.biop.bdv.img.OpenersImageLoader}, which is where
 * you will find this entity.
 * <p>
 * TODO : check if two files with identical names but different path
 * get different ids in the {@link ch.epfl.biop.bdv.img.OpenersImageLoader}
 * Hum, now two different paths should lead to two different FileName entities
 * TO TEST!!
 */

public class FileName extends NamedEntity implements
	Comparable<FileName>
{
	public FileName(int id, String name) {
		super(id, name);
	}
	/**
	 * Compares the {@link #getId() ids}.
	 */
	@Override
	public int compareTo(final FileName o) {
		return getId() - o.getId();
	}

	protected FileName() {}
}
