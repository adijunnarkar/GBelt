package com.example.adityajunnarkar.gbelt;

import android.app.Application;

public class MyApplication extends Application {
    public boolean debug = true;
    public boolean tts = true;
    public boolean recalculation = false;
    String oldbluetoothAddress = "20:16:10:24:54:92";
    String newbluetoothAddress = "20:16:07:05:26:74";
    
    public boolean getTTS() {
        return tts;
    }

    public boolean getDebug() {
        return debug;
    }

    public boolean getRecalculation() {
        return recalculation;
    }

    public String getBTDeviceAddress(){
        return newbluetoothAddress;
    }
}
