package com.chrismin13.automark;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        // Change the title to welcome for the first launch
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean firstLaunch = sharedPreferences.getBoolean("first_launch", true);
        if (firstLaunch) {
            setTitle(getResources().getText(R.string.welcome_title));
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("first_launch", false);
            editor.apply();
        }

        // Populate table with the associations from the dictionary

        TextView left = findViewById(R.id.table_left);

        StringBuilder textBuilder = new StringBuilder();
        for (String key : MainActivity.dictionary.keySet()) {
            textBuilder.append(key).append("\n");
        }
        String text = textBuilder.toString();

        left.setText(text);

        TextView right = findViewById(R.id.table_right);

        textBuilder = new StringBuilder();
        for (String value : MainActivity.dictionary.values()) {
            textBuilder.append(MainActivity.replaceUpper(value)).append("\n");
        }
        text = textBuilder.toString();

        right.setText(text);
    }
}