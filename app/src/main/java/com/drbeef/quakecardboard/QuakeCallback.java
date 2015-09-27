package com.drbeef.quakecardboard;


public interface QuakeCallback {

    void SwitchVRMode();
    void SetEyeBufferResolution(int newResolution);
    void Exit();
}
