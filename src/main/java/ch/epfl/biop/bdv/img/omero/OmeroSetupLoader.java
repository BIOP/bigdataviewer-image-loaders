/*-
 * #%L
 * A nice project implementing an OMERO connection with ImageJ
 * %%
 * Copyright (C) 2021 EPFL
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

import bdv.AbstractViewerSetupImgLoader;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.AbstractIntegerType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import omero.api.RawPixelsStorePrx;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

public class OmeroSetupLoader<T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V> & NativeType<V>, A> extends AbstractViewerSetupImgLoader<T, V> implements MultiResolutionSetupImgLoader< T > {

    final private static Logger logger = LoggerFactory.getLogger(OmeroSetupLoader.class);

    Function<RandomAccessibleInterval<T>, RandomAccessibleInterval<FloatType>> cvtRaiToFloatRai;

    final Converter<T,FloatType> cvt;

    final OmeroBdvOpener opener;
    final int iChannel;

    final Supplier<VolatileGlobalCellCache> cacheSupplier;

    final int numberOfTimePoints;

    final Dimensions[] dimensions;

    final int numMipmapLevels;

    final VoxelDimensions voxelsDimensions;

    final double[][] mmResolutions;

    final CacheArrayLoader<A> loader;

    final int setup;

    public OmeroSetupLoader(OmeroBdvOpener opener,
                            int channelIndex,
                            int setup,
                            T t,
                            V v,
                            Supplier<VolatileGlobalCellCache> cacheSupplier) throws Exception {
        super(t, v);

        this.opener = opener;
        this.iChannel = channelIndex;
        this.setup = setup;
        this.cacheSupplier = cacheSupplier;

        if (t instanceof FloatType) {
            cvt = null;
            cvtRaiToFloatRai = rai -> null;//(RandomAccessibleInterval<FloatType>) rai; // Nothing to be done
        } else if (t instanceof ARGBType) {
            // Average of RGB value
            cvt = (input, output) -> {
                int val = ((ARGBType) input).get();
                int r = ARGBType.red(val);
                int g = ARGBType.green(val);
                int b = ARGBType.blue(val);
                output.set(r+g+b);
            };
            cvtRaiToFloatRai = rai -> Converters.convert( rai, cvt, new FloatType());
        } else if (t instanceof AbstractIntegerType) {
            cvt = (input, output) -> output.set(((AbstractIntegerType) input).getRealFloat());
            cvtRaiToFloatRai = rai -> Converters.convert( rai, cvt, new FloatType());
        } else {
            cvt = null;
            cvtRaiToFloatRai = e -> {
                logger.error("Conversion of "+t.getClass()+" to FloatType unsupported.");
                return null;
            };
        }

        RawPixelsStorePrx reader = null;
        boolean isLittleEndian;

        try {
            // https://forum.image.sc/t/omero-py-how-to-get-tiles-at-different-zoom-level-pyramidal-image/45643/11
            // OMERO always produce big-endian pixels
            isLittleEndian = false;

            reader = opener.pool.acquire();
            numMipmapLevels = opener.getNLevels();

            numberOfTimePoints = opener.sizeT;

            voxelsDimensions = opener.getVoxelDimensions();

            dimensions = new Dimensions[numMipmapLevels];
            for (int level = 0; level < numMipmapLevels; level++) {
                dimensions[level] = getDimensions(
                        opener.getSizeX(level),
                        opener.getSizeY(level),
                        opener.getSizeZ(level));
            }


            // Needs to compute mipmap resolutions... pfou
            mmResolutions = new double[numMipmapLevels][3];
            mmResolutions[0][0] = 1;
            mmResolutions[0][1] = 1;
            mmResolutions[0][2] = 1;

            int[] srcL0dims = new int[] { (int) dimensions[0].dimension(0),
                    (int) dimensions[0].dimension(1), (int) dimensions[0].dimension(2) };
            for (int iLevel = 1; iLevel < numMipmapLevels; iLevel++) {
                int[] srcLidims = new int[] { (int) dimensions[iLevel].dimension(0),
                        (int) dimensions[iLevel].dimension(1), (int) dimensions[iLevel]
                        .dimension(2) };
                mmResolutions[iLevel][0] = (double) srcL0dims[0] /
                        (double) srcLidims[0];
                mmResolutions[iLevel][1] = (double) srcL0dims[1] /
                        (double) srcLidims[1];
                mmResolutions[iLevel][2] = (double) srcL0dims[2] /
                        (double) srcLidims[2];
            }

        }
        finally {
            opener.pool.recycle(reader);
        }

        if (t instanceof UnsignedByteType) {
            loader =
                    (CacheArrayLoader<A>) new OmeroArrayLoaders.OmeroUnsignedByteArrayLoader(
                            opener.pool, iChannel, opener.getNLevels(), (int) dimensions[0].dimension(0),
                            (int) dimensions[0].dimension(1),
                            (int) dimensions[0].dimension(2));
        }
        else if (t instanceof UnsignedShortType) {
            loader =
                    (CacheArrayLoader<A>) new OmeroArrayLoaders.OmeroUnsignedShortArrayLoader(
                            opener.pool, iChannel, opener.getNLevels(), (int) dimensions[0].dimension(0),
                            (int) dimensions[0].dimension(1),
                            (int) dimensions[0].dimension(2), isLittleEndian);
        }
        else if (t instanceof FloatType) {
            loader =
                    (CacheArrayLoader<A>) new OmeroArrayLoaders.OmeroFloatArrayLoader(
                            opener.pool, iChannel, opener.getNLevels(), (int) dimensions[0].dimension(0),
                            (int) dimensions[0].dimension(1),
                            (int) dimensions[0].dimension(2), isLittleEndian);
        }
        else if (t instanceof IntType) {
            loader =
                    (CacheArrayLoader<A>) new OmeroArrayLoaders.OmeroIntArrayLoader(
                            opener.pool, iChannel, opener.getNLevels(), (int) dimensions[0].dimension(0),
                            (int) dimensions[0].dimension(1),
                            (int) dimensions[0].dimension(2), isLittleEndian);
        }
        else if (t instanceof ARGBType) {
            loader =
                    (CacheArrayLoader<A>) new OmeroArrayLoaders.OmeroRGBArrayLoader(
                            opener.pool, iChannel, opener.getNLevels(), (int) dimensions[0].dimension(0),
                            (int) dimensions[0].dimension(1),
                            (int) dimensions[0].dimension(2));
        }
        else {
            throw new UnsupportedOperationException("Pixel type " + t.getClass()
                    .getName() + " unsupported in " + OmeroSetupLoader.class
                    .getName());
        }
    }

    static Dimensions getDimensions(long sizeX, long sizeY, long sizeZ) {
        return new Dimensions() {

            @Override
            public long dimension(int d) {
                if (d == 0) return sizeX;
                if (d == 1) return sizeY;
                return sizeZ;
            }

            @Override
            public int numDimensions() {
                return 3;
            }

            @Override
            public String toString(){
                return "size x "+sizeX+", size y "+sizeY+", size z "+sizeZ;
            }
        };
    }

    //getters
    public Gateway getGateway(){ return opener.getGateway(); }
    public SecurityContext getSecurityContext(){ return opener.getSecurityContext(); }
    public Long getOmeroId(){ return opener.getOmeroId(); }

    @Override
    public RandomAccessibleInterval<V> getVolatileImage(int timepointId,
                       int level, ImgLoaderHint... hints) {
        final long[] dims = dimensions[level].dimensionsAsLongArray();
        final int[] cellDimensions = new int[]{
                opener.getTileSizeX(numMipmapLevels-1-level),
                opener.getTileSizeY(numMipmapLevels-1-level),1};
        final CellGrid grid = new CellGrid(dims, cellDimensions);

        final int priority = this.numMipmapLevels - level;
        final CacheHints cacheHints = new CacheHints(LoadingStrategy.BUDGETED,
                priority, false);

        return cacheSupplier.get().createImg(grid, timepointId, setup, level,
                cacheHints, loader, volatileType);
    }

    @Override
    public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, int level, boolean normalize, ImgLoaderHint... hints) {
        return cvtRaiToFloatRai.apply(getImage(timepointId,level));
    }

    @Override
    public Dimensions getImageSize(int timepointId, int level) {
        return dimensions[level];
    }

    @Override
    public RandomAccessibleInterval<T> getImage(int timepointId, int level, ImgLoaderHint... hints) {
        final long[] dims = dimensions[level].dimensionsAsLongArray();
        final int[] cellDimensions = new int[]{
                opener.getTileSizeX(numMipmapLevels-1-level),
                opener.getTileSizeY(numMipmapLevels-1-level),1};
        final CellGrid grid = new CellGrid(dims, cellDimensions);

        final int priority = this.numMipmapLevels - level;
        final CacheHints cacheHints = new CacheHints(LoadingStrategy.BLOCKING,
                priority, false);

        return cacheSupplier.get().createImg(grid, timepointId, setup, level,
                cacheHints, loader, type);
    }

    @Override
    public double[][] getMipmapResolutions() {
        return mmResolutions;
    }

    @Override
    public AffineTransform3D[] getMipmapTransforms() {
        AffineTransform3D[] ats = new AffineTransform3D[numMipmapLevels];

        for (int iLevel = 0; iLevel < numMipmapLevels; iLevel++) {
            AffineTransform3D at = new AffineTransform3D();
            at.scale(mmResolutions[iLevel][0], mmResolutions[iLevel][1],
                    mmResolutions[iLevel][2]);
            ats[iLevel] = at;
        }

        return ats;
    }

    @Override
    public int numMipmapLevels() {
        return numMipmapLevels;
    }

    @Override
    public RandomAccessibleInterval<FloatType> getFloatImage(int timepointId, boolean normalize, ImgLoaderHint... hints) {
        return cvtRaiToFloatRai.apply(getImage(timepointId,0));
    }

    @Override
    public Dimensions getImageSize(int timepointId) {
        return getImageSize(0,0);
    }

    @Override
    public VoxelDimensions getVoxelSize(int timepointId) {
        return opener.getVoxelDimensions();
    }
}
