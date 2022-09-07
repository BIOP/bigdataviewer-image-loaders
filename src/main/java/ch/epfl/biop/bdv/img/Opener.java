package ch.epfl.biop.bdv.img;

import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;

public interface Opener<T>{

    int getNumMipmapLevels();

    int getNTimePoints();

    AffineTransform3D getTransform();

    ResourcePool<T> getPixelReader();

    VoxelDimensions getVoxelDimensions();

    int[] getCellDimensions(int level);

    Dimensions[] getDimensions();

    //int nChannels();

}
