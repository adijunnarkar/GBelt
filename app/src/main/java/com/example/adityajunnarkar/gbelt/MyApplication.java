package com.example.adityajunnarkar.gbelt;

import android.app.Application;
import android.content.Context;
import android.media.AudioManager;

public class MyApplication extends Application {
    public boolean debug = true;
    public boolean tts = true;
    public boolean recalculation = false;
    String oldHC05Address = "20:16:10:24:54:92";
    String newHC05Address = "20:16:07:05:26:74";
    String headsetAddress = "1C:48:F9:8E:32:04";

    public boolean getTTS() {
        return tts;
    }

    public boolean getDebug() {
        return debug;
    }

    public boolean getRecalculation() {
        return recalculation;
    }

    public String getBTHC05Address(){
        return newHC05Address;
    }

    public String getHeadsetAddress(){
        return headsetAddress;
    }
}
