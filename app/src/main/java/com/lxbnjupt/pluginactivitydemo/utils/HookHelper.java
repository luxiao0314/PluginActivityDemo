package com.lxbnjupt.pluginactivitydemo.utils;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @Description hook AMS和Instrumentation启动activity
 * @Author lux
 * @Date 2022/2/17 3:13 下午
 * @Version
 */
public class HookHelper {

    private static final String TAG = "HookHelper";
    public static final String PLUGIN_INTENT = "plugin_intent";

    /**
     * Hook IActivityManager
     *
     * @throws Exception
     */
    public static void hookAMS() throws Exception {
        Log.e(TAG, "hookAMS");
        Object singleton = null;
        if (Build.VERSION.SDK_INT >= 26) {
            Class<?> activityManageClazz = Class.forName("android.app.ActivityManager");
            // 获取ActivityManager中的IActivityManagerSingleton字段
            Field iActivityManagerSingletonField = ReflectUtils.getField(activityManageClazz, "IActivityManagerSingleton");
            singleton = iActivityManagerSingletonField.get(activityManageClazz);
        } else {
            Class<?> activityManagerNativeClazz = Class.forName("android.app.ActivityManagerNative");
            // 获取ActivityManagerNative中的gDefault字段
            Field gDefaultField = ReflectUtils.getField(activityManagerNativeClazz, "gDefault");
            singleton = gDefaultField.get(activityManagerNativeClazz);
        }

        Class<?> singletonClazz = Class.forName("android.util.Singleton");
        // 获取Singleton中mInstance字段
        Field mInstanceField = ReflectUtils.getField(singletonClazz, "mInstance");
        // 获取IActivityManager
        Object iActivityManager = mInstanceField.get(singleton);

        Class<?> iActivityManagerClazz = Class.forName("android.app.IActivityManager");
        // 获取IActivityManager代理对象
        Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{iActivityManagerClazz}, new IActivityManagerProxy(iActivityManager));

        // 将IActivityManager代理对象赋值给Singleton中mInstance字段
        mInstanceField.set(singleton, proxy);
    }

    /**
     * Hook ActivityThread中Handler成员变量mH
     *
     * @throws Exception
     */
    public static void hookHandler() throws Exception {
        Log.e(TAG, "hookHandler");
        Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
        // 获取ActivityThread中成员变量sCurrentActivityThread字段
        Field sCurrentActivityThreadField = ReflectUtils.getField(activityThreadClazz, "sCurrentActivityThread");
        // 获取ActivityThread主线程对象(应用程序启动后就会在attach方法中赋值)
        Object currentActivityThread = sCurrentActivityThreadField.get(activityThreadClazz);

        // 获取ActivityThread中成员变量mH字段
        Field mHField = ReflectUtils.getField(activityThreadClazz, "mH");
        // 获取ActivityThread主线程中Handler对象
        Handler mH = (Handler) mHField.get(currentActivityThread);

        // 将我们自己的HCallback对象赋值给mH的mCallback
        ReflectUtils.setField(Handler.class, "mCallback", mH, new HCallback(mH));
    }

    public static void hook() {

        try {
            //1.获取IActivityManagerSingleton
            Object IActivityManagerSingleton = getSingletonByVersion();

            //2.获取mInstance
            Class<?> singletonclazz = Class.forName("android.util.Singleton");
            Field mInstanceField = singletonclazz.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);

            if (Build.VERSION.SDK_INT == 29) {
                //Q上需要动态执行create方法
                Method getMethod = singletonclazz.getMethod("get");
                getMethod.setAccessible(true);
                getMethod.invoke(IActivityManagerSingleton);
            }

            Object mInstance = mInstanceField.get(IActivityManagerSingleton);

            //3.动态代理设置自己的mInstance
            Object proxyInstance = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), mInstance.getClass().getInterfaces(), new IActivityManagerProxy(mInstance));

            //4.设置代理的proxyInstance
            mInstanceField.set(IActivityManagerSingleton, proxyInstance);

            //5.获取ActivityThread实例
            Class<?> ActivityThreadclass = Class.forName("android.app.ActivityThread");

            Field sCurrentActivityThreadFiled = ActivityThreadclass.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadFiled.setAccessible(true);
            Object sCurrentActivityThread = sCurrentActivityThreadFiled.get(null);

            //6.获取mH实例
            Field mHFiled = ActivityThreadclass.getDeclaredField("mH");
            mHFiled.setAccessible(true);
            Object mH = mHFiled.get(sCurrentActivityThread);

            Field mCallbackFiled = Handler.class.getDeclaredField("mCallback");
            mCallbackFiled.setAccessible(true);
            //7.设置进入我们自己的Callback

            mCallbackFiled.set(mH, new MyHandlerCallback((Handler) mH));

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TAG------", e.toString());
        }
    }

    /**
     * Hook Instrumentation
     *
     * @param context 上下文环境
     * @throws Exception
     */
    public static void hookInstrumentation(Context context) throws Exception {
        Log.e(TAG, "hookInstrumentation");
        Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
        // 获取ActivityThread中成员变量sCurrentActivityThread字段
        Field sCurrentActivityThreadField = ReflectUtils.getField(activityThreadClazz, "sCurrentActivityThread");
        // 获取ActivityThread中成员变量mInstrumentation字段
        Field mInstrumentationField = ReflectUtils.getField(activityThreadClazz, "mInstrumentation");
        // 获取ActivityThread主线程对象(应用程序启动后就会在attach方法中赋值)
        Object currentActivityThread = sCurrentActivityThreadField.get(activityThreadClazz);
        // 获取Instrumentation对象
        Instrumentation instrumentation = (Instrumentation) mInstrumentationField.get(currentActivityThread);
        PackageManager packageManager = context.getPackageManager();
        // 创建Instrumentation代理对象
        InstrumentationProxy instrumentationProxy = new InstrumentationProxy(instrumentation, packageManager);

        // 用InstrumentationProxy代理对象替换原来的Instrumentation对象
        ReflectUtils.setField(activityThreadClazz, "mInstrumentation", currentActivityThread, instrumentationProxy);
    }

    private static Object getSingletonByVersion() {
        try {
            if (Build.VERSION.SDK_INT == 28) {
                Class<?> clazz = Class.forName("android.app.ActivityManager");
                Field field = clazz.getDeclaredField("IActivityManagerSingleton");
                field.setAccessible(true);
                return field.get(null);
            } else if (Build.VERSION.SDK_INT == 29) {
                Class<?> clazz = Class.forName("android.app.ActivityTaskManager");
                Field field = clazz.getDeclaredField("IActivityTaskManagerSingleton");
                field.setAccessible(true);
                return field.get(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
