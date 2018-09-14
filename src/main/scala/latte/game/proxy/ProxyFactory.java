package latte.game.proxy;

import net.sf.cglib.proxy.Enhancer;

/**
 * Created by linyuhe on 2018/9/14.
 */
public class ProxyFactory {

    @SuppressWarnings("unchecked")
    public static <T> T getSyncProxy(T target) {
        return (T) Enhancer.create(target.getClass(), new SynchronizedInterceptor(target));
    }
}
