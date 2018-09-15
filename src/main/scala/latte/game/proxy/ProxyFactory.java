package latte.game.proxy;

import latte.game.player.Player;
import net.sf.cglib.proxy.Enhancer;

/**
 * Created by linyuhe on 2018/9/14.
 */
public class ProxyFactory {

    @SuppressWarnings("unchecked")
    public static <T> T getSyncProxy(T target) {
        Enhancer e = new Enhancer();
        e.setSuperclass(target.getClass());
        e.setCallback(new SynchronizedInterceptor(target));
        return (T) e.create(new Class[]{Player.class}, new Object[]{null});
    }
}
