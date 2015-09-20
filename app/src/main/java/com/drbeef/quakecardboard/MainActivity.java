package com.drbeef.quakecardboard;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.Window;
import android.view.WindowManager;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.ScreenParams;
import com.google.vrtoolkit.cardboard.Viewport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class MainActivity
        extends CardboardActivity
//            extends Activity
        implements CardboardView.Renderer
//            implements GLSurfaceView.Renderer
{
    int[] texturenames = new int[1];

    private static final String TAG = "QuakeCardboard";

    private static final int GL_RGBA8 = 0x8058;

    private int[] currentFBO = new int[1];
    int fboResolution = 0;

    private Vibrator vibrator;
    private float M_PI = 3.14159265358979323846f;
    public static AudioCallback mAudio;
    //Read these from a file and pass through
    String commandLineParams = new String("quake");

    public static final String vs_Image =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "attribute vec2 a_texCoord;" +
            "varying vec2 v_texCoord;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  v_texCoord = a_texCoord;" +
            "}";

    public static final String fs_Image =
            "precision mediump float;" +
                    "varying vec2 v_texCoord;" +
                    "uniform sampler2D s_texture;" +
                    "void main() {" +
                    "  gl_FragColor = texture2D( s_texture, v_texCoord );" +
                    "}";


    public static int loadShader(int type, String shaderCode){
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    //FBO render eye buffer
    private QuakeFBO fbo;

    //Keep the dimensions
    int mWidth = 0;
    int mHeight = 0;

    private int mPositionHandle;
    private int mTexCoordLoc;

    // Our matrices
    private final float[] mtrxProjection = new float[16];
    private final float[] mtrxView = new float[16];
    private final float[] mtrxProjectionAndView = new float[16];

    // Geometric variables
    public static float vertices[];
    public static short indices[];
    public static float uvs[];
    public FloatBuffer vertexBuffer;
    public ShortBuffer drawListBuffer;
    public FloatBuffer uvBuffer;
    public static int sp_Image;

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
        byte[] buf = new byte[512];
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


    static boolean CreateFBO( QuakeFBO fbo, int offset, int width, int height)
    {
        Log.d(TAG, "CreateFBO");
        // Create the color buffer texture.
        GLES20.glGenTextures(1, fbo.ColorTexture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbo.ColorTexture[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        // Create depth buffer.
        GLES20.glGenRenderbuffers(1, fbo.DepthBuffer, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, fbo.DepthBuffer[0]);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES11Ext.GL_DEPTH_COMPONENT24_OES, width, height);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);

        // Create the frame buffer.
        GLES20.glGenFramebuffers(1, fbo.FrameBuffer, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.FrameBuffer[0]);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, fbo.DepthBuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fbo.ColorTexture[0], 0);
        int renderFramebufferStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if ( renderFramebufferStatus != GLES20.GL_FRAMEBUFFER_COMPLETE )
        {
            Log.d(TAG, "Incomplete frame buffer object!!");
            return false;
        }

        fbo.width = width;
        fbo.height = height;

        return true;
    }

    static void DestroyFBO( QuakeFBO fbo )
    {
        GLES20.glDeleteFramebuffers( 1, fbo.FrameBuffer, 0 );
        fbo.FrameBuffer[0] = 0;
        GLES20.glDeleteRenderbuffers( 1, fbo.DepthBuffer, 0 );
        fbo.DepthBuffer[0] = 0;
        GLES20.glDeleteTextures( 1, fbo.ColorTexture, 0 );
        fbo.ColorTexture[0] = 0;
        fbo.width = 0;
        fbo.height = 0;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (Build.VERSION.SDK_INT>=9)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setEGLConfigChooser(new QuakeEGLConfigChooser());
        //cardboardView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cardboardView.setEGLContextClientVersion(3);
        cardboardView.setEGLContextFactory(new QuakeEGLContextFactory());

        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

         vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        //At the very least ensure we have a directory containing a config file
        copy_asset("config.cfg");

        //Not currently distributing the shareware version
        //copy_asset("pak0.pak");

        //Create the FBOs
        fbo = new QuakeFBO();

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

        mWidth = width;
        mHeight = height;

    }

    @Override
    public void onSurfaceCreated(EGLConfig config) {
         Log.i(TAG, "onSurfaceCreated");

         // Create the shaders, images
         int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vs_Image);
         int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fs_Image);

         sp_Image = GLES20.glCreateProgram();             // create empty OpenGL ES Program
         GLES20.glAttachShader(sp_Image, vertexShader);   // add the vertex shader to program
         GLES20.glAttachShader(sp_Image, fragmentShader); // add the fragment shader to program
         GLES20.glLinkProgram(sp_Image);                  // creates OpenGL ES program executable
     }

    int getDesiredFBOResolution(int viewportWidth) {

        if (viewportWidth > 1024)
            return 1024;
        if (viewportWidth > 512)
            return 512;
        if (viewportWidth > 256)
            return 256;

        //don't want to go lower than this
        return 128;
    }

    @Override
    public void onDrawFrame(HeadTransform headTransform, Eye lefteye, Eye righteye) {

        if (!mQuakeInitialised)
        {
            fboResolution = getDesiredFBOResolution(lefteye.getViewport().width);
            CreateFBO(fbo, 0, fboResolution * 2, fboResolution);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.FrameBuffer[0]);
            QuakeJNILib.setResolution(fboResolution, fboResolution);
            QuakeJNILib.initialise(commandLineParams);

            // Create the image information
            SetupUVCoords();

            mQuakeInitialised = true;
        }

        if (mQuakeInitialised) {
            //eulerAngles[offset + 0] = -pitch;
            //eulerAngles[offset + 1] = -yaw;
            //eulerAngles[offset + 2] = -roll;
            float[] eulerAngles = new float[3];
            headTransform.getEulerAngles(eulerAngles, 0);
            QuakeJNILib.onNewFrame(-eulerAngles[0] / (M_PI / 180.0f), eulerAngles[1] / (M_PI / 180.0f), -eulerAngles[2] / (M_PI / 180.0f));

            // Clear our matrices
            for(int i=0;i<16;i++)
            {
                mtrxProjection[i] = 0.0f;
                mtrxView[i] = 0.0f;
                mtrxProjectionAndView[i] = 0.0f;
            }

            // Create the triangles
            SetupTriangle(0, 0, lefteye.getViewport().width*2, lefteye.getViewport().height);

            // Setup our screen width and height for normal sprite translation.
            Matrix.orthoM(mtrxProjection, 0, 0, lefteye.getViewport().width*2,
                    0, lefteye.getViewport().height, 0, 50);

            // Set the camera position (View matrix)
            Matrix.setLookAtM(mtrxView, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

            // Calculate the projection and view transformation
            Matrix.multiplyMM(mtrxProjectionAndView, 0, mtrxProjection, 0, mtrxView, 0);

            //Record the curent fbo
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, currentFBO, 0);

            //Bind our special fbo
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.FrameBuffer[0]);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);

            GLES20.glScissor(0, 0, fboResolution*2, fboResolution);
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

            //Hopefully type indicates 0 = left, 1 = right
            QuakeJNILib.onDrawEye(0, 0, 0);
            QuakeJNILib.onDrawEye(1, fboResolution, 0);

            //Finished rendering to our frame buffer, now draw this to the target framebuffer
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, currentFBO[0]);

            //eye.getViewport().setGLScissor();
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

            GLES20.glViewport(0, 0, lefteye.getViewport().width*2, lefteye.getViewport().height);

            // Set our shader programm
            GLES20.glUseProgram(sp_Image);

            // get handle to vertex shader's vPosition member
            mPositionHandle = GLES20.glGetAttribLocation(sp_Image, "vPosition");

            // Enable generic vertex attribute array
            GLES20.glEnableVertexAttribArray(mPositionHandle);

            // Prepare the triangle coordinate data
            GLES20.glVertexAttribPointer(mPositionHandle, 3,
                    GLES20.GL_FLOAT, false, 0, vertexBuffer);

            // Get handle to texture coordinates location
            mTexCoordLoc = GLES20.glGetAttribLocation(sp_Image, "a_texCoord" );

            // Enable generic vertex attribute array
            GLES20.glEnableVertexAttribArray(mTexCoordLoc);

            // Prepare the texturecoordinates
            GLES20.glVertexAttribPointer(mTexCoordLoc, 2, GLES20.GL_FLOAT,
                    false, 0, uvBuffer);

            // Get handle to shape's transformation matrix
            int mtrxhandle = GLES20.glGetUniformLocation(sp_Image, "uMVPMatrix");

            // Apply the projection and view transformation
            GLES20.glUniformMatrix4fv(mtrxhandle, 1, false, mtrxProjectionAndView, 0);

            // Get handle to textures locations
            int mSamplerLoc = GLES20.glGetUniformLocation (sp_Image, "s_texture" );

            // Bind texture to fbo's color texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbo.ColorTexture[0]);

            // Set the sampler texture unit to our fbo's color texture
            GLES20.glUniform1i(mSamplerLoc, 0);

            // Draw the triangle
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length,
                    GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR)
                Log.d(TAG, "GLES20 Error = " + error);

            // Disable vertex array
            GLES20.glDisableVertexAttribArray(mPositionHandle);
            GLES20.glDisableVertexAttribArray(mTexCoordLoc);
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
    //@Override
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
        return false;
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


    public void SetupUVCoords()
    {
        // Create our UV coordinates.
        uvs = new float[] {
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f
        };

        // The texture buffer
        ByteBuffer bb = ByteBuffer.allocateDirect(uvs.length * 4);
        bb.order(ByteOrder.nativeOrder());
        uvBuffer = bb.asFloatBuffer();
        uvBuffer.put(uvs);
        uvBuffer.position(0);

        // Generate Textures, if more needed, alter these numbers.
        GLES20.glGenTextures(1, texturenames, 0);

        // Retrieve our image from resources.
        int id = getResources().getIdentifier("mipmap/ic_launcher", null, getPackageName());

        // Temporary create a bitmap
        Bitmap bmp = BitmapFactory.decodeResource(getResources(), id);

        // Bind texture to texturename
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturenames[0]);

        // Set filtering
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        // Load the bitmap into the bound texture.
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);

        // We are done using the bitmap so we should recycle it.
        bmp.recycle();

    }

    public void SetupTriangle(int x, int y, int width, int height)
    {
        // We have to create the vertices of our triangle.
        vertices = new float[]
                {
                        x, y + height, 0.0f,
                        x, y, 0.0f,
                        x + width, y, 0.0f,
                        x + width, y + height, 0.0f,
                };
        indices = new short[] {0, 1, 2, 0, 2, 3}; // The order of vertexrendering.

        // The vertex buffer.
        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        // initialize byte buffer for the draw list
        ByteBuffer dlb = ByteBuffer.allocateDirect(indices.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(indices);
        drawListBuffer.position(0);
    }
}
