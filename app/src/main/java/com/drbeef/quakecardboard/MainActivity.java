package com.drbeef.quakecardboard;


import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.khronos.egl.EGLConfig;


public class MainActivity
        extends CardboardActivity
        implements CardboardView.StereoRenderer
{

    private static final String TAG = "QuakeCardboard";

    private Vibrator vibrator;
    private float M_PI = 3.14159265358979323846f;
    public static AudioCallback mAudio;
    //Read these from a file and pass through
    String commandLineParams = new String("quake");

    public static boolean mQuakeInitialised = false;

    static {
        try {
            Log.i("JNI", "Trying to load libquakecardboard.so");
            System.loadLibrary("quakecardboard");
        } catch (UnsatisfiedLinkError ule) {
            Log.e("JNI", "WARNING: Could not load libquakecardboard.so");
        }
    }

    public void copy_asset(String name) {
        File f = new File("/sdcard/QGVR/id1/" + name);
        if (!f.exists() ||
                //If file was somehow corrupted, copy the back-up
                f.length() < 500) {

            //Ensure we have an appropriate folder
            new File("/sdcard/QGVR/id1").mkdirs();
            copy_asset(name, "/sdcard/QGVR/id1/" + name);
        }
    }

    public void copy_asset(String name_in, String name_out) {
        AssetManager assets = this.getAssets();

        try {
            InputStream in = assets.open(name_in);
            OutputStream out = new FileOutputStream(name_out);

            copy_stream(in, out);

            out.close();
            in.close();

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    public static void copy_stream(InputStream in, OutputStream out)
            throws IOException {
        byte[] buf = new byte[1024];
        while (true) {
            int count = in.read(buf);
            if (count <= 0)
                break;
            out.write(buf, 0, count);
        }
    }


    public void patchConfig(String dir) throws Exception
    {
        if (new File(dir+"/id1/config.cfg").exists())
        {
            BufferedReader br=new BufferedReader(new FileReader(dir+"/id1/config.cfg"));
            String s;
            StringBuilder sb=new StringBuilder(0);
            boolean needsPatching = false;
            while ((s=br.readLine())!=null)
            {
                if (!s.contains("cl_forwardspeed") &&
                        !s.contains("cl_backspeed"))
                {
                    sb.append(s+"\n");
                }
                else
                    needsPatching = true;
            }
            br.close();

            if (needsPatching)
            {
                FileWriter fw=new FileWriter(dir+"/id1/config.cfg");
                fw.write(sb.toString());fw.flush();fw.close();
            }
        }

    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setEGLConfigChooser(new QuakeEGLConfigChooser( ));
        cardboardView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cardboardView.setEGLContextClientVersion(2);

        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        //At the very least ensure we have a directory containing a config file
        copy_asset("config.cfg");

        //Not currently distributing the shareware version
        //copy_asset("pak0.pak");


        if (mAudio==null)
        {
            mAudio = new AudioCallback();
        }

        QuakeJNILib.setCallbackObject(mAudio);


        //See if user is trying to use command line params
        if(new File("/sdcard/QGVR/commandline.txt").exists())
        {
            BufferedReader br;
            try {
                br = new BufferedReader(new FileReader("/sdcard/QGVR/commandline.txt"));
                String s;
                StringBuilder sb=new StringBuilder(0);
                while ((s=br.readLine())!=null)
                    sb.append(s + " ");
                br.close();

                commandLineParams = new String(sb.toString());
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height)
    {
        Log.d(TAG, "onSurfaceChanged width = " + width + "  height = " + height);
    }

     @Override
    public void onSurfaceCreated(EGLConfig config)
     {

         Log.i(TAG, "onSurfaceCreated");

     }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {

        if (mQuakeInitialised) {
            //eulerAngles[offset + 0] = -pitch;
            //eulerAngles[offset + 1] = -yaw;
            //eulerAngles[offset + 2] = -roll;
            float[] eulerAngles = new float[3];
            headTransform.getEulerAngles(eulerAngles, 0);

            QuakeJNILib.onNewFrame(-eulerAngles[0] / (M_PI / 180.0f), eulerAngles[1] / (M_PI / 180.0f), eulerAngles[2] / (M_PI / 180.0f));
        }
    }

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        if (!mQuakeInitialised && eye.getType() == 2)
        {
            Log.d(TAG, "x = " + eye.getViewport().x);
            Log.d(TAG, "y = " + eye.getViewport().y);
            Log.d(TAG, "width = " + eye.getViewport().width);
            Log.d(TAG, "height = " + eye.getViewport().height);
            QuakeJNILib.setResolution(eye.getViewport().width, eye.getViewport().height);
            QuakeJNILib.initialise(commandLineParams);
            mQuakeInitialised = true;
        }

        if (mQuakeInitialised) {
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            eye.getViewport().setGLScissor();
            eye.getViewport().setGLViewport();

            GLES20.glClearColor(0.5f, 0.0f, 0.5f, 1.0f);

            //Hopefully type indicates 0 = left, 1 = right
            QuakeJNILib.onDrawEye(eye.getType() - 1, eye.getViewport().x, eye.getViewport().y);
        }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
        if (mQuakeInitialised) {
            QuakeJNILib.onFinishFrame();
        }
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");

        QuakeJNILib.onKeyEvent(K_ENTER, KeyEvent.ACTION_DOWN, 0);

        // Always give user feedback.
        vibrator.vibrate(50);
    }


    public int getCharacter(int keyCode, KeyEvent event)
    {
        if (keyCode==KeyEvent.KEYCODE_DEL) return '\b';
        return event.getUnicodeChar();
    }

    @Override public boolean dispatchKeyEvent( KeyEvent event )
    {
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        int character = 0;

        if ( action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP )
        {
            return super.dispatchKeyEvent( event );
        }
        if ( action == KeyEvent.ACTION_UP )
        {
            Log.v( TAG, "GLES3JNIActivity::dispatchKeyEvent( " + keyCode + ", " + action + " )" );
        }

        //Following buttons must not be handled here
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL
                )
            return false;

        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            //Pass through
            QuakeJNILib.onKeyEvent( keyCode, action, character );
        }

        //Convert to Quake keys
        character = getCharacter(keyCode, event);
        int qKeyCode = convertKeyCode(keyCode, event);

        //Don't hijack all keys (volume etc)
        if (qKeyCode != -1)
            keyCode = qKeyCode;

        QuakeJNILib.onKeyEvent( keyCode, action, character );
        return true;
    }

    private static float getCenteredAxis(MotionEvent event,
                                         int axis) {
        final InputDevice.MotionRange range = event.getDevice().getMotionRange(axis, event.getSource());
        if (range != null) {
            final float flat = range.getFlat();
            final float value = event.getAxisValue(axis);
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }


    //Save the game pad type once known:
    // 1 - Generic BT gamepad
    // 2 - Samsung gamepad that uses different axes for right stick
    int gamepadType = 0;

    int lTrigAction = KeyEvent.ACTION_UP;
    int rTrigAction = KeyEvent.ACTION_UP;

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int source = event.getSource();
        int action = event.getAction();
        if ((source==InputDevice.SOURCE_JOYSTICK)||(event.getSource()==InputDevice.SOURCE_GAMEPAD))
        {
            if (event.getAction() == MotionEvent.ACTION_MOVE)
            {
                float x = getCenteredAxis(event, MotionEvent.AXIS_X);
                float y = -getCenteredAxis(event, MotionEvent.AXIS_Y);
                QuakeJNILib.onTouchEvent( source, action, x, y );

                float z = getCenteredAxis(event, MotionEvent.AXIS_Z);
                float rz = 0.0f;//getCenteredAxis(event, MotionEvent.AXIS_RZ);
                //For the samsung game pad (uses different axes for the second stick)
                float rx = getCenteredAxis(event, MotionEvent.AXIS_RX);
                float ry = 0.0f;//getCenteredAxis(event, MotionEvent.AXIS_RY);

                //let's figure it out
                if (gamepadType == 0)
                {
                    if (z != 0.0f || rz != 0.0f)
                        gamepadType = 1;
                    else if (rx != 0.0f || ry != 0.0f)
                        gamepadType = 2;
                }

                switch (gamepadType)
                {
                    case 0:
                        break;
                    case 1:
                        QuakeJNILib.onMotionEvent( source, action, z, rz );
                        break;
                    case 2:
                        QuakeJNILib.onMotionEvent( source, action, rx, ry );
                        break;
                }

                //Fire weapon using shoulder trigger
                float axisRTrigger = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                        event.getAxisValue(MotionEvent.AXIS_GAS));
                int newRTrig = axisRTrigger > 0.6 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                if (rTrigAction != newRTrig)
                {
                    QuakeJNILib.onKeyEvent( K_MOUSE1, newRTrig, 0);
                    rTrigAction = newRTrig;
                }

                //Run using L shoulder
                float axisLTrigger = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                        event.getAxisValue(MotionEvent.AXIS_BRAKE));
                int newLTrig = axisLTrigger > 0.6 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                if (lTrigAction != newLTrig)
                {
                    QuakeJNILib.onKeyEvent( K_SHIFT, newLTrig, 0);
                    lTrigAction = newLTrig;
                }
            }
        }
        return false;
    }

    private float max(float axisValue, float axisValue2) {
        return (axisValue > axisValue2) ? axisValue : axisValue2;
    }

    @Override public boolean dispatchTouchEvent( MotionEvent event) {
        int source = event.getSource();
        int action = event.getAction();
        float x = event.getRawX();
        float y = event.getRawY();
        if ( action == MotionEvent.ACTION_UP )
        {
            Log.v( TAG, "GLES3JNIActivity::dispatchTouchEvent( " + action + ", " + x + ", " + y + " )" );
        }
        QuakeJNILib.onTouchEvent( source, action, x, y );

        return false;
    }

    public static final int K_TAB = 9;
    public static final int K_ENTER = 13;
    public static final int K_ESCAPE = 27;
    public static final int K_SPACE	= 32;
    public static final int K_BACKSPACE	= 127;
    public static final int K_UPARROW = 128;
    public static final int K_DOWNARROW = 129;
    public static final int K_LEFTARROW = 130;
    public static final int K_RIGHTARROW = 131;
    public static final int K_ALT = 132;
    public static final int K_CTRL = 133;
    public static final int K_SHIFT = 134;
    public static final int K_F1 = 135;
    public static final int K_F2 = 136;
    public static final int K_F3 = 137;
    public static final int K_F4 = 138;
    public static final int K_F5 = 139;
    public static final int K_F6 = 140;
    public static final int K_F7 = 141;
    public static final int K_F8 = 142;
    public static final int K_F9 = 143;
    public static final int K_F10 = 144;
    public static final int K_F11 = 145;
    public static final int K_F12 = 146;
    public static final int K_INS = 147;
    public static final int K_DEL = 148;
    public static final int K_PGDN = 149;
    public static final int K_PGUP = 150;
    public static final int K_HOME = 151;
    public static final int K_END = 152;
    public static final int K_PAUSE = 153;
    public static final int K_NUMLOCK = 154;
    public static final int K_CAPSLOCK = 155;
    public static final int K_SCROLLOCK = 156;
    public static final int K_MOUSE1 = 512;
    public static final int K_MOUSE2 = 513;
    public static final int K_MOUSE3 = 514;
    public static final int K_MWHEELUP = 515;
    public static final int K_MWHEELDOWN = 516;
    public static final int K_MOUSE4 = 517;
    public static final int K_MOUSE5 = 518;

    public static int convertKeyCode(int keyCode, KeyEvent event)
    {
        switch(keyCode)
        {
            case KeyEvent.KEYCODE_FOCUS:
                return K_F1;
            case KeyEvent.KEYCODE_DPAD_UP:
                return K_UPARROW;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return K_DOWNARROW;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return 'a';
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return 'd';
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return K_CTRL;
            case KeyEvent.KEYCODE_ENTER:
                return K_ENTER;
			case KeyEvent.KEYCODE_BACK:
				return K_ESCAPE;
            case KeyEvent.KEYCODE_DEL:
                return K_BACKSPACE;
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return K_ALT;
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return K_SHIFT;
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return K_CTRL;
            case KeyEvent.KEYCODE_INSERT:
                return K_INS;
            case 122:
                return K_HOME;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return K_DEL;
            case 123:
                return K_END;
            case KeyEvent.KEYCODE_ESCAPE:
                return K_ESCAPE;
            case KeyEvent.KEYCODE_TAB:
                return K_TAB;
            case KeyEvent.KEYCODE_F1:
                return K_F1;
            case KeyEvent.KEYCODE_F2:
                return K_F2;
            case KeyEvent.KEYCODE_F3:
                return K_F3;
            case KeyEvent.KEYCODE_F4:
                return K_F4;
            case KeyEvent.KEYCODE_F5:
                return K_F5;
            case KeyEvent.KEYCODE_F6:
                return K_F6;
            case KeyEvent.KEYCODE_F7:
                return K_F7;
            case KeyEvent.KEYCODE_F8:
                return K_F8;
            case KeyEvent.KEYCODE_F9:
                return K_F9;
            case KeyEvent.KEYCODE_F10:
                return K_F10;
            case KeyEvent.KEYCODE_F11:
                return K_F11;
            case KeyEvent.KEYCODE_F12:
                return K_F12;
            case KeyEvent.KEYCODE_CAPS_LOCK:
                return K_CAPSLOCK;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return K_PGDN;
            case KeyEvent.KEYCODE_PAGE_UP:
                return K_PGUP;
            case KeyEvent.KEYCODE_BUTTON_A:
                return K_ENTER;
            case KeyEvent.KEYCODE_BUTTON_B:
                return 'r';
            case KeyEvent.KEYCODE_BUTTON_X:
                return '#'; //prev weapon, set in the config.txt as impulse 12
            case KeyEvent.KEYCODE_BUTTON_Y:
                return '/';//Next weapon, set in the config.txt as impulse 10
            //These buttons are not so popular
            case KeyEvent.KEYCODE_BUTTON_C:
                return 'a';//That's why here is a, nobody cares.
            case KeyEvent.KEYCODE_BUTTON_Z:
                return 'z';
            //--------------------------------
            case KeyEvent.KEYCODE_BUTTON_START:
                return K_ESCAPE;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                return K_ENTER;
            case KeyEvent.KEYCODE_MENU:
                return K_ESCAPE;

            //Both shoulder buttons will "fire"
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2:
                return K_MOUSE1;

            //enables "run"
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2:
                return K_SHIFT;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                return -1;
        }
        int uchar = event.getUnicodeChar(0);
        if((uchar < 127)&&(uchar!=0))
            return uchar;
        return keyCode%95+32;//Magic
    }

}
