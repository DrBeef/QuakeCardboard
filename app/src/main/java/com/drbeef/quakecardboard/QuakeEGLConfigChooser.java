package com.drbeef.quakecardboard;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;


class QuakeEGLConfigChooser implements GLSurfaceView.EGLConfigChooser {
    private static final String TAG = "QuakeCardboard";

    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {

        int[] mConfigSpec = {
                EGL10.EGL_SAMPLES, 0,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE };

        int[] num_config = new int[1];
//        egl.eglChooseConfig(display, mConfigSpec, null, 0, num_config);

        EGLConfig[] configs = new EGLConfig[1000];
//        egl.eglChooseConfig(display, mConfigSpec, configs, numConfigs,
//                num_config);

        egl.eglGetConfigs(display, configs, 1000, num_config);

        for ( int i = 0; i < num_config[0]; i++ ) {
            Log.d(TAG,
                    "EGL config " + i + " :"
                            + printConfig(egl, display, configs[i]));
        }

        for ( int i = 0; i < num_config[0]; i++ )
        {
/*            if (findConfigAttrib( egl, display, configs[i], EGL10.EGL_RENDERABLE_TYPE, -1) != 4)
            {
                continue;
            }

            int[] surfaceType = new int[1];
            egl.eglGetConfigAttrib( display, configs[i], EGL10.EGL_SURFACE_TYPE , surfaceType);
            if ( ( surfaceType[0] & ( EGL10.EGL_WINDOW_BIT | EGL10.EGL_PBUFFER_BIT ) ) !=
                    ( EGL10.EGL_WINDOW_BIT | EGL10.EGL_PBUFFER_BIT ) )
            {
                continue;
            }
*/
            int	j = 0;
            for ( ; mConfigSpec[j] != EGL10.EGL_NONE; j += 2 )
            {
                int[] attrib = new int[1];
                egl.eglGetConfigAttrib( display, configs[i], mConfigSpec[j] , attrib);
                if ( attrib[0] != mConfigSpec[j + 1] )
                {
                    Log.d(TAG,
                            "EGL config " + i + " :" + printConfig(egl, display, configs[i]) +
                                    "  Rejected: Attribute = " + j + " value = " + attrib[0]);
                    break;
                }
            }

            if ( mConfigSpec[j] == EGL10.EGL_NONE )
            {
                //Found one!
                Log.d(TAG,
                        "selected EGL config : "
                                + printConfig(egl, display, configs[i]));
                return configs[i];
            }
        }

        // best choice : select first config
        Log.d(TAG,
                "best choice : select first EGL config : "
                        + printConfig(egl, display, configs[0]));

        return configs[0];

    }

    private String printConfig(EGL10 egl, EGLDisplay display,
                               EGLConfig config) {

        int msaa = findConfigAttrib(egl, display, config, EGL10.EGL_SAMPLES,
                0);
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

        return String.format("EGLConfig msaa=%d rgba=%d%d%d%d depth=%d stencil=%d",
                msaa, r, g, b, a, d, s)
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

/*
public class QuakeEGLConfigChooser implements GLSurfaceView.EGLConfigChooser {
    int r;
    int g;
    int b;
    int a;
    int msaa;
    boolean gl2;
    EGL10 eglcmp;
    EGLDisplay dspcmp;

    public QuakeEGLConfigChooser(int inr,int ing,int inb,int ina,int inmsaa,boolean gles2) {
        r=inr;
        g=ing;
        b=inb;
        a=ina;
        msaa=inmsaa;
        gl2=gles2;
    }

    //EGLConfig[] configs=new EGLConfig[1000];
    //int numconfigs=0;
    class comprtr implements Comparator<EGLConfig>
    {
        @Override
        public int compare(EGLConfig lhs, EGLConfig rhs) {
            int tmp[]=new int[1];
            int lr,lg,lb,la,ld,ls;
            int rr,rg,rb,ra,rd,rs;
            int rat1,rat2;
            eglcmp.eglGetConfigAttrib(dspcmp, lhs, EGL10.EGL_RED_SIZE, tmp);lr=tmp[0];
            eglcmp.eglGetConfigAttrib(dspcmp, lhs, EGL10.EGL_GREEN_SIZE, tmp);lg=tmp[0];
            eglcmp.eglGetConfigAttrib(dspcmp, lhs, EGL10.EGL_BLUE_SIZE, tmp);lb=tmp[0];
            eglcmp.eglGetConfigAttrib(dspcmp, lhs, EGL10.EGL_ALPHA_SIZE, tmp);la=tmp[0];
            //eglcmp.eglGetConfigAttrib(dspcmp, lhs, EGL10.EGL_DEPTH_SIZE, tmp);ld=tmp[0];
            //eglcmp.eglGetConfigAttrib(dspcmp, lhs, EGL10.EGL_STENCIL_SIZE, tmp);ls=tmp[0];
            eglcmp.eglGetConfigAttrib(dspcmp, rhs, EGL10.EGL_RED_SIZE, tmp);rr=tmp[0];
            eglcmp.eglGetConfigAttrib(dspcmp, rhs, EGL10.EGL_GREEN_SIZE, tmp);rg=tmp[0];
            eglcmp.eglGetConfigAttrib(dspcmp, rhs, EGL10.EGL_BLUE_SIZE, tmp);rb=tmp[0];
            eglcmp.eglGetConfigAttrib(dspcmp, rhs, EGL10.EGL_ALPHA_SIZE, tmp);ra=tmp[0];
            //eglcmp.eglGetConfigAttrib(dspcmp, rhs, EGL10.EGL_DEPTH_SIZE, tmp);rd=tmp[0];
            //eglcmp.eglGetConfigAttrib(dspcmp, rhs, EGL10.EGL_STENCIL_SIZE, tmp);rs=tmp[0];
            rat1=(Math.abs(lr-r)+Math.abs(lg-g)+Math.abs(lb-b));//*1000000-(ld*10000+la*100+ls);
            rat2=(Math.abs(rr-r)+Math.abs(rg-g)+Math.abs(rb-b));//*1000000-(rd*10000+ra*100+rs);
            return Integer.valueOf(rat1).compareTo(Integer.valueOf(rat2));
        }
    }

    public int[] intListToArr(ArrayList<Integer> integers)
    {
        int[] ret=new int[integers.size()];
        Iterator<Integer> iterator=integers.iterator();
        for (int i=0;i<ret.length;i++)
        {
            ret[i]=iterator.next().intValue();
        }
        return ret;
    }

    @Override
    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
        dspcmp=display;
        eglcmp=egl;

        int[] tmp=new int[1];
        ArrayList<Integer> alst=new ArrayList<Integer>(0);
        alst.add(EGL10.EGL_SAMPLE_BUFFERS);alst.add((msaa>0)?1:0);
        alst.add(EGL10.EGL_SAMPLES);alst.add(msaa);

        //TODO tegra zbuf
        //alst.add(0x30E2);alst.add(0x30E3);
        alst.add(EGL10.EGL_RED_SIZE);alst.add(r);
        alst.add(EGL10.EGL_GREEN_SIZE);alst.add(g);
        alst.add(EGL10.EGL_BLUE_SIZE);alst.add(b);
        alst.add(EGL10.EGL_ALPHA_SIZE);alst.add(a);
        if (gl2)
        {alst.add(EGL10.EGL_RENDERABLE_TYPE);alst.add(4);}
        alst.add(EGL10.EGL_DEPTH_SIZE);alst.add(32);
        alst.add(EGL10.EGL_STENCIL_SIZE);alst.add(8);
        alst.add(EGL10.EGL_NONE);
        int[] pararr=intListToArr(alst);
        EGLConfig[] configs=new EGLConfig[1000];
        while (tmp[0]==0)
        {
            egl.eglChooseConfig(display,pararr,configs,1000,tmp);
            pararr[pararr.length-4]-=4;
            if (pararr[pararr.length-4]<0)
            {
                pararr[pararr.length-4]=32;
                pararr[pararr.length-2]-=4;
                if (pararr[pararr.length-2]<0)
                {
                    if (pararr[0]!=0x30E0)
                    {
                        pararr[0]=0x30E0;
                        pararr[2]=0x30E1;
                        pararr[pararr.length-4]=32;
                        pararr[pararr.length-2]=8;
                    }
                    else
                    {
                        //LOLWUT?! Let's crash.
                        return null;
                    }
                }
            }
        }
        //numconfigs=tmp[0];
        Arrays.sort(configs, 0, tmp[0], new comprtr());
        return configs[0];
    }
}
*/
