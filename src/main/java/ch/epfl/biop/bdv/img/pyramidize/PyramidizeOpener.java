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

import bdv.img.cache.VolatileGlobalCellCache;
import ch.epfl.biop.bdv.img.OpenerSetupLoader;
import ch.epfl.biop.bdv.img.ResourcePool;
import ch.epfl.biop.bdv.img.opener.Opener;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;

import java.io.IOException;
import java.util.function.Supplier;

public class PyramidizeOpener<T> implements Opener<T> {

    final Opener<T> origin;

    final int nResolutionLevels;

    Dimensions[] dimensions;

    public PyramidizeOpener(Opener<T> origin) {
        this.origin = origin;
        long maxX = origin.getDimensions()[0].dimension(0); // Highest resolution level, dimension in X
        long maxY = origin.getDimensions()[0].dimension(1); // Highest resolution level, dimension in Y
        int nResolutionLevels = 1;
        while ((nResolutionLevels<6)&&(maxX>64)&&(maxY>64)) {
            nResolutionLevels++;
            maxX/=2.0;
            maxY/=2.0;
        }
        this.nResolutionLevels = nResolutionLevels;

        dimensions = new Dimensions[nResolutionLevels];
        dimensions[0] = origin.getDimensions()[0]; // Highest resolution level = original one
        long currentDimX = dimensions[0].dimension(0);
        long currentDimY = dimensions[0].dimension(1);
        long currentDimZ = dimensions[0].dimension(2);

        for (int level = 1; level<nResolutionLevels; level++) {
            currentDimX = currentDimX / 2;
            currentDimY = currentDimY / 2;
            dimensions[level] = getDimension(currentDimX, currentDimY, currentDimZ);
        }

    }


    static int tileSize(int level) {
        return (int) Math.pow(2,8-level); // 2^9 = 256
    }

    protected Opener<T> getOrigin() {
        return origin;
    }

    @Override
    public int[] getCellDimensions(int level) {
        // Highest resolution -> take the original tile size
        if (level==0) return origin.getCellDimensions(0);
        // Else take the downscaled version
        int tileSize = tileSize(level);
        return new int[]{tileSize, tileSize, 1};
    }

    @Override
    public Dimensions[] getDimensions() {
        return dimensions;
    }

    @Override
    public int getNChannels() {
        return origin.getNChannels();
    }

    @Override
    public int getNTimePoints() {
        return origin.getNTimePoints();
    }

    @Override
    public int getNumMipmapLevels() {
        return nResolutionLevels;
    }

    @Override
    public ResourcePool<T> getPixelReader() {
        return origin.getPixelReader();
    }

    @Override
    public Type<? extends NumericType<?>> getPixelType() {
        return origin.getPixelType();
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return origin.getVoxelDimensions();
    }

    @Override
    public boolean isLittleEndian() {
        return origin.isLittleEndian();
    }

    @Override
    public String getImageFormat() {
        return origin.getImageFormat();
    }

    @Override
    public OpenerSetupLoader<?, ?, ?> getSetupLoader(int channelIdx, int setupIdx, Supplier<VolatileGlobalCellCache> cacheSupplier) {
        return new PyramidizeSetupLoader<>(this, channelIdx, setupIdx, cacheSupplier);
    }

    @Override
    public String getRawPixelDataKey() {
        return origin.getRawPixelDataKey()+".pyramid";
    }

    @Override
    public OpenerMeta getMeta() {
        return origin.getMeta();
    }

    @Override
    public void close() throws IOException {
        origin.close();
    }


    static Dimensions getDimension(long sizeX, long sizeY, long sizeZ) {
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
        };
    }
}
