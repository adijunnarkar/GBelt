package com.example.adityajunnarkar.gbelt;

import android.app.Application;

public class MyApplication extends Application {
    public boolean debug = true;
    public boolean tts = true;
    public boolean recalculation = false;

    public boolean getTTS() {
        return tts;
    }

    public boolean getDebug() {
        return debug;
    }

    public boolean getRecalculation() {
        return recalculation;
    }
}
