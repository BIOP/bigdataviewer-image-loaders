package ch.epfl.biop.bdv.img;

import ch.epfl.biop.bdv.img.bioformats.ChannelProperties;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.NumericType;

import java.util.List;

public interface Opener<T> {

    int getNumMipmapLevels();

    int getNTimePoints();

    AffineTransform3D getTransform();

    ResourcePool<T> getPixelReader();

    VoxelDimensions getVoxelDimensions();

    int[] getCellDimensions(int level);

    Dimensions[] getDimensions();

    int getNChannels();

    Type<? extends NumericType> getPixelType();
    List<ChannelProperties> getChannel(int iChannel);
    List<Entity> getEntities();//channel

    String getImageName();

}
