package org.g_okuyama.capture.pro;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import android.hardware.Camera;
import android.hardware.Camera.Size;

public class Reflect {   
    private static Method Parameters_getSupportedPreviewSizes;   
  
    static {
        initCompatibility();   
    };   
  
    private static void initCompatibility() {   
        try {   
            Parameters_getSupportedPreviewSizes = Camera.Parameters.class  
                    .getMethod("getSupportedPreviewSizes", new Class[] {});   
        } catch (NoSuchMethodException nsme) {}   
    }   
  
    @SuppressWarnings("unchecked")   
    public static List<Size> getSupportedPreviewSizes(Camera.Parameters p) {   
        try {   
            if (Parameters_getSupportedPreviewSizes != null) {   
                return (List<Size>) Parameters_getSupportedPreviewSizes   
                        .invoke(p);   
            } else {   
                return null;   
            }   
        } catch (InvocationTargetException ite) {   
            Throwable cause = ite.getCause();   
            if (cause instanceof RuntimeException) {   
                throw (RuntimeException) cause;   
            } else if (cause instanceof Error) {   
                throw (Error) cause;   
            } else {   
                throw new RuntimeException(ite);   
            }   
        } catch (IllegalAccessException ie) {   
            return null;   
        }   
    }   
}