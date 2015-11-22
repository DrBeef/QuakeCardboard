package com.drbeef.quakecardboard;

public class QuakeJNILib {

    // Input
    public static native void onKeyEvent( int keyCode, int action, int character );
    public static native void onTouchEvent( int source, int action, float x, float y );
    public static native void onMotionEvent( int source, int action, float x, float y );

    //Rendering and lifecycle
    public static native void setResolution( int width, int height );
    public static native void initialise( String gameFolder, String commandLineParams );
    public static native void onNewFrame( float pitch, float yaw, float roll );
    public static native void onDrawEye( int eye, int x, int y );
    public static native void onFinishFrame( );
    public static native void onSwitchVRMode( );
    public static native void onBigScreenMode( int mode );
    public static native int  getCentreOffset( );

    //Audio
    public static native void requestAudioData();
    public static native void setCallbackObjects(Object obj1, Object obj2);
}
