package ch.epfl.biop.bdv.img;

import bdv.img.cache.VolatileGlobalCellCache;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;

import java.io.Closeable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Interface for all specific openers.
 *
 * Contains a list of common methods to retrieve necessary objects to load images on BDV
 * and create SpimData instances.
 *
 * This interface has a parameter {@param <T>} to be compatible with the different openers (BioFormats, OMERO).
 * It corresponds to the type of {@link ResourcePool}.
 *
 */
public interface Opener<T> extends Closeable {

    /**
     * @param level of resolution
     * @return the size of each square that is loaded for the specified resolution {@param level}
     */
    int[] getCellDimensions(int level);


    /**
     * @param iChannel
     * @return properties of the specified channel {@param iChannel}
     */
    ChannelProperties getChannel(int iChannel);


    /**
     * @return the image dimensions in X,Y and Z
     */
    Dimensions[] getDimensions();


    /**
     * @param iChannel
     * @return the list of {@link Entity} for the specified channel {@param iChannel}
     * that are then added to a {@link mpicbg.spim.data.sequence.ViewSetup}
     */
    List<Entity> getEntities(int iChannel);


    /**
     * @return the image name
     */
    String getImageName();


    /**
     * @return the number of channels of the image
     */
    int getNChannels();


    /**
     * @return the number of frames of the image
     */
    int getNTimePoints();


    /**
     * @return the number of resolution levels of the image
     */
    int getNumMipmapLevels();


    /**
     * @return a specific reader depending on if the image is coming from OMERO or BioFormats
     */
    ResourcePool<T> getPixelReader();


    /**
     * @return BDV compatible pixel type
     */
    Type<? extends NumericType> getPixelType();


    /**
     * @return the AffineTransform used to recover the original display
     */
    AffineTransform3D getTransform();


    /**
     * @return pixel's size in unit/pixel
     */
    VoxelDimensions getVoxelDimensions();

    /**
     * @return pixel's endianness
     */
    boolean isLittleEndian();

    /**
     * @return opener's setup loader
     */
    BiopSetupLoader<?,?,?> getSetupLoader(int channelIdx, int setupIdx,
                                          Supplier<VolatileGlobalCellCache> cacheSupplier);

    /**
     * @return A string that identifies uniquely the raw pixel data of this
     * opener, and the way the raw data are opened
     *
     * This is used to avoid loading several times the same data. If rawDataKeys
     * are equals, and given the fact that the data is read only, the setup loaders
     * from duplicated data will be redirected to a single one.
     */
    String getRawPixelDataKey();

}
