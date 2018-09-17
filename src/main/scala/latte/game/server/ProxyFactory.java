package latte.game.server;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Created by linyuhe on 2018/9/14.
 */
public class ProxyFactory {

    /**
     * Note: 不支持private字段，请使用private[this]
     * @param target
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Component> T getSyncProxy(T target) {
        Enhancer e = new Enhancer();
        e.setSuperclass(target.getClass());
        e.setCallback(new SynchronizedInterceptor(target));
        return (T) e.create(new Class[]{Player.class}, new Object[]{null});
    }
}

class SynchronizedInterceptor<T> implements MethodInterceptor {

    private final T target;

    public SynchronizedInterceptor(T target) {
        this.target = target;
    }

    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        if (Modifier.isPublic(method.getModifiers())) {
            synchronized (obj) {
                return method.invoke(target, args);
            }
        } else {
            return method.invoke(target, args);
        }
    }
}
