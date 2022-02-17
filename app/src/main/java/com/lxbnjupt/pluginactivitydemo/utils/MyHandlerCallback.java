package com.lxbnjupt.pluginactivitydemo.utils;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;

import java.lang.reflect.Field;
import java.util.List;

public class MyHandlerCallback implements Handler.Callback {
        private Handler mHandler;

        public MyHandlerCallback(Handler handler) {
            mHandler = handler;
        }

        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.what == 159) {
                Object obj = msg.obj;
                try {
                    //获取ClientTransaction中的mActivityCallbacks集合
                    Class<?> clazz = Class.forName("android.app.servertransaction" +
                            ".ClientTransaction");
                    Field mActivityCallbacksFiled = clazz.getDeclaredField("mActivityCallbacks");
                    mActivityCallbacksFiled.setAccessible(true);
                    List list = (List) mActivityCallbacksFiled.get(obj);
                    if (list != null && list.size() > 0) {
                        //得到集合中的LaunchActivityItem
                        Object o = list.get(0);
                        //获取LaunchActivityItem中的mIntent
                        Class<?> LaunchActivityItemClazz = Class.forName("android.app" +
                                ".servertransaction.LaunchActivityItem");
                        Field mIntentFiled = LaunchActivityItemClazz.getDeclaredField("mIntent");
                        mIntentFiled.setAccessible(true);
                        Intent intent = (Intent) mIntentFiled.get(o);

                        //得到我们设置的class 替换进去
                        Intent pluginIntent = intent.getParcelableExtra(HookHelper.PLUGIN_INTENT);
                        // 将启动SubActivity的Intent替换为启动PluginActivity的Intent
                        intent.setComponent(pluginIntent.getComponent());
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            mHandler.handleMessage(msg);
            return true;
        }
    }