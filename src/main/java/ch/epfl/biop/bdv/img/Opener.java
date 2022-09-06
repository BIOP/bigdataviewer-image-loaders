package ch.epfl.biop.bdv.img;

import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;

public interface Opener<T>{

    int getNumMipmapLevels(int iSerie);

    int getNTimePoints(int iSerie);

    AffineTransform3D getTransform(int iSerie);

    ResourcePool<T> getPixelReader();

    VoxelDimensions getVoxelDimensions(int iSerie);


}
