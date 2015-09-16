package com.drbeef.quakecardboard;


import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class QuakeEGLContextFactory implements GLSurfaceView.EGLContextFactory {

    private static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static String TAG = new String("QuakeCardboard");

    private EGLSurface tinySurface;

    @Override
    public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config)
    {
        int[] version = new int[2];
        egl.eglInitialize(display, version);

        int contextAttribs[] = new int[]
                {
                        EGL_CONTEXT_CLIENT_VERSION, 3,
                        EGL10.EGL_NONE
                };
        EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, contextAttribs );
/*
        int surfaceAttribs[] = new int[]
            {
                    EGL10.EGL_WIDTH, 16,
                    EGL10.EGL_HEIGHT, 16,
                    EGL10.EGL_NONE
            };

        Log.d(TAG, "        TinySurface = eglCreatePbufferSurface( Display, Config, surfaceAttribs )");
        tinySurface = egl.eglCreatePbufferSurface(display, config, surfaceAttribs);
        if ( tinySurface == EGL10.EGL_NO_SURFACE )
        {
            Log.d(TAG, "        eglCreatePbufferSurface() failed: "+ egl.eglGetError());
            egl.eglDestroyContext(display, context);
            context = EGL10.EGL_NO_CONTEXT;
            return context;
        }
        Log.d(TAG, "        eglMakeCurrent( Display, TinySurface, TinySurface, Context )" );
        if ( !egl.eglMakeCurrent( display, tinySurface, tinySurface, context ) )
        {
            Log.d(TAG, "        eglMakeCurrent() failed: " + egl.eglGetError()  );
            egl.eglDestroySurface(display, tinySurface);
            egl.eglDestroyContext(display, context);
            context = EGL10.EGL_NO_CONTEXT;
            return context;
        }*/

        return context;
    }

    @Override
    public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context)
    {
        egl.eglDestroyContext(display, context);
    }

}
