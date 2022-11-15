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

package ch.epfl.biop.bdv.img.legacy.bioformats;

import ch.epfl.biop.bdv.img.ResourcePool;
import loci.formats.IFormatReader;

import java.util.function.Supplier;

/**
 * Created with IntelliJ IDEA. User: dbtsai Date: 2/24/13 Time: 1:21 PM
 */
@Deprecated
public class ReaderPool extends ResourcePool<IFormatReader> {

	final Supplier<IFormatReader> readerSupplier;

	public ReaderPool(int size, Boolean dynamicCreation,
		Supplier<IFormatReader> readerSupplier)
	{
		super(size, dynamicCreation);
		createPool();
		this.readerSupplier = readerSupplier;
	}

	@Override
	public IFormatReader createObject() {
		return readerSupplier.get();
	}

}