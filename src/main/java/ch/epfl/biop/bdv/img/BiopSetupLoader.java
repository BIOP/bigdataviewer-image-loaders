package ch.epfl.biop.bdv.img;

import bdv.AbstractViewerSetupImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.Volatile;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;

abstract public class BiopSetupLoader<T extends NumericType<T> & NativeType<T>, V extends Volatile<T> & NumericType<V> & NativeType<V>, A>
        extends AbstractViewerSetupImgLoader<T, V> implements
        MultiResolutionSetupImgLoader<T> {
    public BiopSetupLoader(T type, V volatileType) {
        super(type, volatileType);
    }

}
