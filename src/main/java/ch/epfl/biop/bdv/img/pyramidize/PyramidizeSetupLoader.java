/*-
 * #%L
 * Various image loaders for bigdataviewer (Bio-Formats, Omero, QuPath)
 * %%
 * Copyright (C) 2022 - 2025 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
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
import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.OpenerSetupLoader;
import ch.epfl.biop.bdv.img.opener.OpenerHelper;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.basictypeaccess.DataAccess;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static ch.epfl.biop.bdv.img.pyramidize.PyramidizeOpener.tileSize;

public class PyramidizeSetupLoader<T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V> & NativeType<V>, A extends DataAccess & ArrayDataAccess<A>>
        extends OpenerSetupLoader<T,V,A> {

    final PyramidizeOpener<?> opener;

    final Supplier<VolatileGlobalCellCache> cacheSupplier;
    final OpenerSetupLoader<T,V,A> level0SetupLoader;

    final AffineTransform3D[] ats;

    final double[][] mmResolutions;

    final int setup;

    List<List<RandomAccessibleInterval<T>>> raiTL = new ArrayList<>();
    List<List<RandomAccessibleInterval<V>>> raiTLV = new ArrayList<>();

    protected PyramidizeSetupLoader(PyramidizeOpener<?> opener, int channelIdx, int setupIdx,
                                    Supplier<VolatileGlobalCellCache> cacheSupplier) {
        super((T) opener.getPixelType(), (V) OpenerHelper.getVolatileOf((T) opener.getPixelType()));
        this.opener = opener;
        this.cacheSupplier = cacheSupplier;
        this.setup = setupIdx;
        level0SetupLoader = (OpenerSetupLoader<T, V, A>) opener.getOrigin().getSetupLoader(channelIdx, setupIdx, cacheSupplier);

        ats = new AffineTransform3D[opener.getNumMipmapLevels()];
        ats[0] = level0SetupLoader.getMipmapTransforms()[0];

        double[] tr = ats[0].getTranslation();

        for (int level = 1; level<opener.nResolutionLevels; level++) {
            AffineTransform3D transform = new AffineTransform3D();
            transform.translate(-tr[0], -tr[1], -tr[2]);
            transform.scale(Math.pow(2, level), Math.pow(2, level), 1);
            transform.translate(tr);
            ats[level] = transform;
        }

        mmResolutions = new double[opener.nResolutionLevels][3];
        mmResolutions[0][0] = 1;
        mmResolutions[0][1] = 1;
        mmResolutions[0][2] = 1;

        // compute mipmap levels
        for (int iLevel = 1; iLevel < opener.nResolutionLevels; iLevel++) {
            double downscalingFactor = Math.pow(2, iLevel);
            mmResolutions[iLevel][0] = downscalingFactor;
            mmResolutions[iLevel][1] = downscalingFactor;
            mmResolutions[iLevel][2] = 1;
        }

    }

    volatile boolean hasBeenInitialised = false;

    void ensureInitialisation() {
        // delayed initialisation to allow the override of the cache
        if (hasBeenInitialised) return;
        // here: not initialisation
        synchronized (this) {
            if (hasBeenInitialised) return;

            T t = (T) opener.getPixelType();

            PyramidizeArrayLoaders.PyramidizeArrayLoader loader;
            if (t instanceof UnsignedByteType) {
                loader = new PyramidizeArrayLoaders.PyramidizeUnsignedByteArrayLoader(this);
            } else if (t instanceof ByteType) {
                loader = new PyramidizeArrayLoaders.PyramidizeByteArrayLoader(this);
            } else if (t instanceof UnsignedShortType) {
                loader = new PyramidizeArrayLoaders.PyramidizeUnsignedShortArrayLoader(this);
            } else if (t instanceof ShortType) {
                loader = new PyramidizeArrayLoaders.PyramidizeShortArrayLoader(this);
            } else if (t instanceof FloatType) {
                loader = new PyramidizeArrayLoaders.PyramidizeFloatArrayLoader(this);
            } else if (t instanceof IntType) {
                loader = new PyramidizeArrayLoaders.PyramidizeIntArrayLoader(this);
            } else if (t instanceof ARGBType) {
                loader = new PyramidizeArrayLoaders.PyramidizeARGBArrayLoader(this);
            } else {
                throw new UnsupportedOperationException("Pixel type " + t.getClass()
                        .getName() + " unsupported in " + PyramidizeSetupLoader.class
                        .getName());
            }

            for (int tp = 0; tp< opener.getNTimePoints(); tp++) {
                raiTL.add(new ArrayList<>());
                raiTLV.add(new ArrayList<>());

                for (int level = 1; level< opener.nResolutionLevels; level++) {
                    int tileSizeLevel = tileSize(level);
                    final int[] cellDimensions = new int[]{ tileSizeLevel, tileSizeLevel, 1 };
                    final RandomAccessibleInterval<T> raiBelow;
                    if (level==1) {
                        raiBelow = getImage(tp,0);
                    } else {
                        raiBelow = raiTL.get(tp).get(level-2);
                    }
                    long[] newDimensions = new long[3];
                    newDimensions[0] = raiBelow.dimensionsAsLongArray()[0]/2;
                    newDimensions[1] = raiBelow.dimensionsAsLongArray()[1]/2;
                    newDimensions[2] = raiBelow.dimensionsAsLongArray()[2];
                    CellGrid grid = new CellGrid(newDimensions, cellDimensions);

                    int priority = opener.nResolutionLevels - level;
                    CacheHints cacheHints = new CacheHints(LoadingStrategy.BLOCKING,
                            priority, false);

                    raiTL.get(tp).add(cacheSupplier.get().createImg(grid, tp, setup, level,
                            cacheHints, (CacheArrayLoader<A>) loader, type));

                    priority = opener.nResolutionLevels - level;
                    cacheHints = new CacheHints(LoadingStrategy.BUDGETED,
                            priority, false);

                    raiTLV.get(tp).add(cacheSupplier.get().createImg(grid, tp, setup, level,
                            cacheHints, (CacheArrayLoader<A>) loader, volatileType));

                }
            }
            hasBeenInitialised = true;
            loader.init();
        }
    }

    @Override
    public RandomAccessibleInterval<V> getVolatileImage(int timepointId, int level, ImgLoaderHint... hints) {
        if (level == 0) return level0SetupLoader.getVolatileImage(timepointId, level, hints);
        ensureInitialisation();
        return raiTLV.get(timepointId).get(level-1);
    }

    @Override
    public Dimensions getImageSize(int timepointId, int level) {
        return opener.getDimensions()[level];
    }

    @Override
    public RandomAccessibleInterval<T> getImage(int timepointId, int level, ImgLoaderHint... hints) {
        if (level==0) return level0SetupLoader.getImage(timepointId, level, hints);
        ensureInitialisation();
        return raiTL.get(timepointId).get(level-1);
    }

    @Override
    public double[][] getMipmapResolutions() {
        return mmResolutions;
    }

    @Override
    public AffineTransform3D[] getMipmapTransforms() {
        return ats;
    }

    @Override
    public int numMipmapLevels() {
        return opener.getNumMipmapLevels();
    }

    @Override
    public VoxelDimensions getVoxelSize(int timepointId) {
        return opener.getVoxelDimensions();
    }

}
