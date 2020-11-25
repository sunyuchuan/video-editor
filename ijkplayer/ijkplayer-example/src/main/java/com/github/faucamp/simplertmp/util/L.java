package com.github.faucamp.simplertmp.util;

import android.util.Log;
/**
 * Simple short-hand logging converter class; modify as necessary by whatever
 * logging mechanism you are using
 * 
 * @author francois
 */
public class L {
    private static final String TAG = "rtmp-java";

    public static boolean isDebugEnabled() {
        return true;
    }

    public static void t(String message) {        
        Log.d(TAG, message);
    }

    public static void d(String message) {
        Log.d(TAG, message);
    }
    
    public static void i(String message) {
        Log.i(TAG, message);
    }
    
    public static void w(String message) {
        Log.w(TAG, message);
    }
    
    public static void w(String message, Throwable t) {
        Log.w(TAG, message);
        t.printStackTrace();
    }
  
    public static void e(String message) {
        Log.e(TAG, message);
    }
    
    public static void e(String message, Throwable t) {
        Log.e(TAG, message);
        t.printStackTrace();
    }
}
