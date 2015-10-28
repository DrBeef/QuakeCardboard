package com.drbeef.quakecardboard;


public interface QuakeCallback {

    void SwitchVRMode();
    void BigScreenMode(int mode);
    void SetEyeBufferResolution(int newResolution);
    void Exit();
}
