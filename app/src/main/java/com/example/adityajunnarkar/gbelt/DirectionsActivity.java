package com.example.adityajunnarkar.gbelt;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.List;

import Modules.Route;

public class DirectionsActivity extends ListActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // grab data from NavigationActivity
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();
        Route mRoute = (Route) bundle.getSerializable("route");

        // save and parse HTML instructions
        List<CharSequence> instructions = new ArrayList<CharSequence>();

        for(int i = 0; i < mRoute.steps.size(); i++)
        {
            instructions.add(Html.fromHtml(mRoute.steps.get(i).htmlInstruction));

        }

        // display the instructions in the ListView
        ArrayAdapter<CharSequence> notes =
                new ArrayAdapter<CharSequence>(this, R.layout.activity_directions, instructions);
        setListAdapter(notes);
    }

    public void onBackPressed() {
        finish();
    }
}
