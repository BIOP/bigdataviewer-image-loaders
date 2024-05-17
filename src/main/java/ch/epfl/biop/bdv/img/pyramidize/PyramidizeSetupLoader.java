package ch.epfl.biop.bdv.img.pyramidize;

import bdv.cache.SharedQueue;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.volatiles.VolatileViews;
import ch.epfl.biop.bdv.img.OpenerSetupLoader;
import ch.epfl.biop.bdv.img.opener.OpenerHelper;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.basictypeaccess.DataAccess;
import net.imglib2.img.basictypeaccess.array.ArrayDataAccess;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static ch.epfl.biop.bdv.img.pyramidize.PyramidizeOpener.tileSize;

public class PyramidizeSetupLoader<T extends RealType<T> & NativeType<T>, V extends Volatile<T> & RealType<V> & NativeType<V>, A extends DataAccess & ArrayDataAccess<A>>
        extends OpenerSetupLoader<T,V,A> {

    final PyramidizeOpener<T> opener;

    final Supplier<VolatileGlobalCellCache> cacheSupplier;
    final OpenerSetupLoader<T,V,A> level0SetupLoader;

    final AffineTransform3D[] ats;

    final double[][] mmResolutions;

    final int setup;

    List<List<RandomAccessibleInterval<T>>> raiTL = new ArrayList<>();
    List<List<RandomAccessibleInterval<V>>> raiTLV = new ArrayList<>();

    protected PyramidizeSetupLoader(PyramidizeOpener opener, int channelIdx, int setupIdx,
                                    Supplier<VolatileGlobalCellCache> cacheSupplier,
                                    SharedQueue queue) {
        super((T) opener.getPixelType(), (V) OpenerHelper.getVolatileOf((T) opener.getPixelType()));
        this.opener = opener;
        this.cacheSupplier = cacheSupplier;
        this.setup = setupIdx;
        level0SetupLoader = opener.getOrigin().getSetupLoader(channelIdx, setupIdx, cacheSupplier);

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

                // Expand image by one pixel to avoid out of bounds exception
                final RandomAccessibleInterval<T> rai =  Views.expandBorder(raiBelow,1,1,0);

                // Creates shifted views by one pixel in x, y, and x+y : quadrant averaging
                RandomAccessibleInterval<T> rai00 = Views.subsample(rai,2,2,1);
                RandomAccessibleInterval<T> rai01 = Views.subsample(Views.offsetInterval(rai, new long[]{1,0,0}, raiBelow.dimensionsAsLongArray()),2,2,1);
                RandomAccessibleInterval<T> rai10 = Views.subsample(Views.offsetInterval(rai, new long[]{0,1,0}, raiBelow.dimensionsAsLongArray()),2,2,1);
                RandomAccessibleInterval<T> rai11 = Views.subsample(Views.offsetInterval(rai, new long[]{1,1,0}, raiBelow.dimensionsAsLongArray()),2,2,1);

                LoadedCellCacheLoader loader = LoadedCellCacheLoader.get(grid, cell -> {
                    // Cursor on the source image
                    final Cursor<T> c00 = Views.flatIterable(Views.interval(rai00, cell)).cursor();
                    final Cursor<T> c01 = Views.flatIterable(Views.interval(rai01, cell)).cursor();
                    final Cursor<T> c10 = Views.flatIterable(Views.interval(rai10, cell)).cursor();
                    final Cursor<T> c11 = Views.flatIterable(Views.interval(rai11, cell)).cursor();

                    // Cursor on output image
                    Cursor<T> out = Views.flatIterable(cell).cursor();

                    while (out.hasNext()) {
                        float val =
                                c00.next().getRealFloat()
                                        +c01.next().getRealFloat()
                                        +c10.next().getRealFloat()
                                        +c11.next().getRealFloat();
                        out.next().setReal(val/4.0);
                    }

                }, (T) opener.getPixelType(), AccessFlags.setOf(AccessFlags.VOLATILE));


                Cache<Long, Cell<T>> cache = (new GlobalLoaderCache(this, tp, level)).withLoader(loader);

                CachedCellImg img = new CachedCellImg(grid, (T) opener.getPixelType(), cache, ArrayDataAccessFactory.get((T) opener.getPixelType(), AccessFlags.setOf(AccessFlags.VOLATILE)));

                raiTL.get(tp).add(img);
                raiTLV.get(tp).add(VolatileViews.wrapAsVolatile(raiTL.get(tp).get(level-1), queue));
            }

        }


    }

    @Override
    public RandomAccessibleInterval<V> getVolatileImage(int timepointId, int level, ImgLoaderHint... hints) {
        if (level == 0) return level0SetupLoader.getVolatileImage(timepointId, level, hints);
        return raiTLV.get(timepointId).get(level-1);
    }

    @Override
    public Dimensions getImageSize(int timepointId, int level) {
        return opener.getDimensions()[level];
    }

    @Override
    public RandomAccessibleInterval<T> getImage(int timepointId, int level, ImgLoaderHint... hints) {
        if (level==0) return level0SetupLoader.getImage(timepointId, level, hints);
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
