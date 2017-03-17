package com.example.adityajunnarkar.gbelt;

import android.app.Application;

public class MyApplication extends Application {
    public boolean debug = false;
    public boolean tts = true;
    public boolean recalculation = true;
//    String bluetoothAddress = "20:16:10:24:54:92"; // Prototype
    String bluetoothAddress = "20:16:07:05:26:74"; // Final


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
        return bluetoothAddress;
    }
}
