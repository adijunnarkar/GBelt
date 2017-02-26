package com.example.adityajunnarkar.gbelt;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.Serializable;

public class SplashActivity extends AppCompatActivity implements Serializable {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, VoiceModeActivity.class);
        Bundle bundle = new Bundle(); // pass bundle to voice mode activity

        bundle.putSerializable("activity", (Serializable) "Maps");
        intent.putExtras(bundle);

        bundle.putSerializable("origin", (Serializable) "Your Location");
        intent.putExtras(bundle);

        bundle.putSerializable("destination", (Serializable) "");
        intent.putExtras(bundle);

        bundle.putSerializable("mode", (Serializable) 1); // walking
        intent.putExtras(bundle);

        startActivity(intent);
        finish();
    }
}
