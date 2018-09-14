package latte.game.proxy;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Created by linyuhe on 2018/9/14.
 */
public class SynchronizedInterceptor<T> implements MethodInterceptor {

    private final T target;

    public SynchronizedInterceptor(T target) {
        this.target = target;
    }

    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        if (Modifier.isPublic(method.getModifiers())) {
            synchronized (target) {
                return method.invoke(target, args);
            }
        } else {
            return method.invoke(target, args);
        }
    }
}
