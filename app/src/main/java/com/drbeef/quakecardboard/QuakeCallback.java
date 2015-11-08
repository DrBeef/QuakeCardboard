package com.drbeef.quakecardboard;


public interface QuakeCallback {

    void SwitchVRMode();
    void BigScreenMode(int mode);
    void SwitchStereoMode(int stereo_mode);
    void SetEyeBufferResolution(int newResolution);
    void SwapEyes();
    void Exit();
}
