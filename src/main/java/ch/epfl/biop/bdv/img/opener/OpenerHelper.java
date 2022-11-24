package ch.epfl.biop.bdv.img.opener;

import net.imglib2.Volatile;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileIntType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

public class OpenerHelper {
    private static Logger logger = LoggerFactory.getLogger(OpenerHelper.class);

    static public <T> T memoize(String key, Map<String, Object> cache, Supplier<T> getter) {
        if (!cache.containsKey(key)) {
            cache.put(key, getter.get());
        } else {
            logger.debug(key+" is reused! ");
        }
        return (T) cache.get(key);
    }

    /**
     *
     * @param t
     * @return volatile pixel type from t
     */
    static public Volatile getVolatileOf(NumericType t) {
        if (t instanceof UnsignedShortType) return new VolatileUnsignedShortType();

        if (t instanceof IntType) return new VolatileIntType();

        if (t instanceof UnsignedByteType) return new VolatileUnsignedByteType();

        if (t instanceof FloatType) return new VolatileFloatType();

        if (t instanceof ARGBType) return new VolatileARGBType();
        return null;
    }
}
