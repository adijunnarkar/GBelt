package com.example.adityajunnarkar.gbelt;

import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.Serializable;

public class SplashActivity extends AppCompatActivity implements Serializable {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // put in a delay to remove the flicker bug
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {

                Intent intent = new Intent(SplashActivity.this, VoiceModeActivity.class);
                Bundle bundle = new Bundle(); // pass bundle to voice mode activity

                bundle.putSerializable("activity", (Serializable) "Maps");
                intent.putExtras(bundle);

                bundle.putSerializable("origin", (Serializable) "Your Location");
                intent.putExtras(bundle);

                bundle.putSerializable("destination", (Serializable) "");
                intent.putExtras(bundle);

                bundle.putSerializable("mode", (Serializable) 1); // walking
                intent.putExtras(bundle);

                bundle.putSerializable("tripStarted", (Serializable) false); // walking
                intent.putExtras(bundle);

                startActivity(intent);
                finish();
                handler.removeCallbacks(this);
            }
        }, 500);
    }
}
