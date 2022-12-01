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
