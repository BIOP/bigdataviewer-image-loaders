/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2024 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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
package ch.epfl.biop.bdv.img;

import bdv.AbstractViewerSetupImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.Volatile;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

/**
 * This class aims at being as generic as possible to be extended by all setupLoaders.
 *
 * @param <T> pixel type
 * @param <V> volatile pixel type
 * @param <A> AccessType {@link ArrayDataAccess}
 */

abstract public class OpenerSetupLoader<T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V> & NativeType<V>, A>
        extends AbstractViewerSetupImgLoader<T, V> implements
        MultiResolutionSetupImgLoader<T> {
    public OpenerSetupLoader(T type, V volatileType) {
        super(type, volatileType);
    }

}
