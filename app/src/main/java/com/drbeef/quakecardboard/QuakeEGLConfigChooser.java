package com.drbeef.quakecardboard;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

class QuakeEGLConfigChooser implements GLSurfaceView.EGLConfigChooser {
    private static final String TAG = "QuakeCardboard";

    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {

        int[] mConfigSpec = {
                EGL10.EGL_SAMPLE_BUFFERS, 0,
                EGL10.EGL_SAMPLES, 0,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_NONE };

        int[] num_config = new int[1];
        egl.eglChooseConfig(display, mConfigSpec, null, 0, num_config);

        int numConfigs = num_config[0];

        if (numConfigs <= 0) {
            throw new IllegalArgumentException(
                    "No EGL configs match configSpec");
        }

        EGLConfig[] configs = new EGLConfig[numConfigs];
        egl.eglChooseConfig(display, mConfigSpec, configs, numConfigs,
                num_config);

        //Find best config
        for (EGLConfig config : configs) {
            Log.i(TAG,
                    "found EGL config : "
                            + printConfig(egl, display, config));

            int[] surfaceType = new int[1];
            egl.eglGetConfigAttrib( display, config, EGL10.EGL_SURFACE_TYPE , surfaceType);
            if ( ( surfaceType[0] & ( EGL10.EGL_WINDOW_BIT | EGL10.EGL_PBUFFER_BIT ) ) !=
                    ( EGL10.EGL_WINDOW_BIT | EGL10.EGL_PBUFFER_BIT ) )
            {
                continue;
            }

            //Found one!
            Log.d(TAG,
                    "selected EGL config : "
                            + printConfig(egl, display, config));
            return config;
        }

        // best choice : select first config
        Log.d(TAG,
                "best choice : select first EGL config : "
                        + printConfig(egl, display, configs[0]));

        return configs[0];

    }

    private String printConfig(EGL10 egl, EGLDisplay display,
                               EGLConfig config) {

        int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE,
                0);
        int g = findConfigAttrib(egl, display, config,
                EGL10.EGL_GREEN_SIZE, 0);
        int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE,
                0);
        int a = findConfigAttrib(egl, display, config,
                EGL10.EGL_ALPHA_SIZE, 0);
        int d = findConfigAttrib(egl, display, config,
                EGL10.EGL_DEPTH_SIZE, 0);
        int s = findConfigAttrib(egl, display, config,
                EGL10.EGL_STENCIL_SIZE, 0);

        return String.format("EGLConfig rgba=%d%d%d%d depth=%d stencil=%d",
                r, g, b, a, d, s)
                + " native="
                + findConfigAttrib(egl, display, config,
                EGL10.EGL_NATIVE_RENDERABLE, 0)
                + " buffer="
                + findConfigAttrib(egl, display, config,
                EGL10.EGL_BUFFER_SIZE, 0)
                + String.format(
                " caveat=0x%04x",
                findConfigAttrib(egl, display, config,
                        EGL10.EGL_CONFIG_CAVEAT, 0));

    }

    private int findConfigAttrib(EGL10 egl, EGLDisplay display,
                                 EGLConfig config, int attribute, int defaultValue) {

        int[] mValue = new int[1];
        if (egl.eglGetConfigAttrib(display, config, attribute, mValue)) {
            return mValue[0];
        }
        return defaultValue;
    }

} // end of QuakeEGLConfigChooser
