package com.drbeef.quakecardboard;

public class QuakeFBO {

    public int[] FrameBuffer;
    public int[] DepthBuffer;
    public int[] ColorTexture;
    public int height;
    public int width;

    public QuakeFBO()
    {
        this.FrameBuffer = new int[1];
        this.FrameBuffer[0] = 0;
        this.DepthBuffer = new int[1];
        this.DepthBuffer[0] = 0;
        this.ColorTexture = new int[1];
        this.ColorTexture[0] = 0;
    }
}
