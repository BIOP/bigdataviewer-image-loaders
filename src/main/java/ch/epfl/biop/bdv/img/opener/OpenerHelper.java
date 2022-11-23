package ch.epfl.biop.bdv.img.opener;

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
}
