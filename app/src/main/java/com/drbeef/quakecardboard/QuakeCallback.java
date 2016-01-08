package com.drbeef.quakecardboard;


public interface QuakeCallback {

    void SwitchVRMode(int vrMode);
    void BigScreenMode(int mode);
    void SwitchStereoMode(int stereo_mode);
    void SetEyeBufferResolution(int newResolution);
    void Exit();
}
