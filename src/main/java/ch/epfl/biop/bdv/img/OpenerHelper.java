package ch.epfl.biop.bdv.img;

import java.util.Map;
import java.util.function.Supplier;

public class OpenerHelper {

     static public <T> T memoize(String key, Map<String, Object> cache, Supplier<T> getter) {
        if (!cache.containsKey(key)) {
            cache.put(key, getter.get());
        } else {
            System.out.println(key+" is reused! ");
        }
        return (T) cache.get(key);
    }
}
