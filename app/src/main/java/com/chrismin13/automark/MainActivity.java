package com.chrismin13.automark;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.chrismin13.automark.utils.UndoRedoHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {

    // Using tree map as it is sorted and we need the conversion to happen in a specific order
    // E.g. άνω τελεία and τελεία
    public static TreeMap<String, String> dictionary = new TreeMap<String, String>() {{
        put(" κόμμα ", ", ");
        put(" άνω τελεία ", "· ");
        put(" άνω τέλεια ", "· ");
        put(" άνω κάτω τέλεια ", ": ");
        put(" άνω κάτω τελεία ", ": ");
        put(" άνω και κάτω τέλεια ", ": ");
        put(" άνω και κάτω τελεία ", ": ");
        put(" τελεία ", ". UPPER");
        put(" τέλεια ", ". UPPER");
        put(" θαυμαστικό ", "! UPPER");
        put(" ερωτηματικό ", "; UPPER");
        put(" άνοιγμα εισαγωγικών ", " «UPPER");
        put(" κλείσιμο εισαγωγικών ", "» ");
        put(" ανοιγμα παρένθεσης ", " (");
        put(" άνοιγμα παρένθεσης ", " (");
        put(" κλείσιμο παρένθεσης ", ") ");
        put(" άνοιγμα παρενθέσεις ", " (");
        put(" ανοιγμα παρενθέσεις ", " (");
        put(" κλείσιμο παρενθέσεις ", ") ");
    }};

    public static String tag = "AutoMark DEBUG: ";
    private static MainActivity instance; // Make sure it's private so that it cannot be set

    UndoRedoHelper textHelper; // Keep track of all changes in the text box
    private Menu menu; // The top bar

    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        instance = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start tracking changes for the text
        TextView textView = findViewById(R.id.main_text);
        textHelper = new UndoRedoHelper(textView);

        // Listener for convert text button
        findViewById(R.id.convert_button).setOnClickListener(v -> convertText());

        // Listener for copy text button
        findViewById(R.id.copy_button).setOnClickListener(v -> copyText());

        // Get any stored data
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Show the help screen on first launch
        boolean firstLaunch = sharedPreferences.getBoolean("first_launch", true);
        if (firstLaunch) {
            startActivity(new Intent(getApplicationContext(), HelpActivity.class));
        }

        // Display the saved text if the option is enabled
        boolean saveText = sharedPreferences.getBoolean("save_text", false);
        if(saveText) {
            textView.setText(sharedPreferences.getString("saved_text", ""));
        }

    }

    @Override
    protected void onPause() {
        // Save the text
        // Doing this in onPause because I don't trust the LMK of OEMs
        super.onPause();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        TextView textView = findViewById(R.id.main_text);
        editor.putString("saved_text", textView.getText().toString());
        editor.apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Add all the menu options to the top bar
        this.menu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Get the id of the selected menu option
        int id = item.getItemId();
        if (id == R.id.help) {
            startActivity(new Intent(getApplicationContext(), HelpActivity.class));
        } else if (id == R.id.settings) {
            startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
        } else if (id == R.id.undo) {
            if (textHelper != null && textHelper.getCanUndo()) {
                textHelper.undo();
            }
        } else if (id == R.id.redo) {
            if (textHelper != null && textHelper.getCanRedo()) {
                textHelper.redo();
            }
        } else if (id == R.id.clear) {
            TextView textView = findViewById(R.id.main_text);
            if (textView.getText().length() > 0) // Prevent unnecessary edits, helps with undo
                textView.setText("");
        } else if (id == R.id.share) {
            share();
        } else if (id == R.id.save) {
            save();
        } else if (id == R.id.load) {
            load();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Deactivate the undo and redo buttons on launch
        updateUndoRedoStatus();
        return true;
    }

    public void updateUndoRedoStatus() {
        if (menu == null)
            return;

        MenuItem item = menu.findItem(R.id.undo);
        if (textHelper != null && textHelper.getCanUndo()) {
            item.setEnabled(true);
            item.getIcon().setAlpha(255);
        } else {
            item.setEnabled(false);
            item.getIcon().setAlpha(130);
        }

        item = menu.findItem(R.id.redo);
        if (textHelper != null && textHelper.getCanRedo()) {
            item.setEnabled(true);
            item.getIcon().setAlpha(255);
        } else {
            item.setEnabled(false);
            item.getIcon().setAlpha(130);
        }
    }

    public void convertText() {
        // Get the text from the textview
        TextView textView = findViewById(R.id.main_text);
        String text = textView.getText().toString();

        text = text + " "; // Add a space at the end in case it ends on a punctuation mark that hot word

//        Log.i(tag, "Text is: " + text);
//        Log.i(tag, "Dictionary keys are: " + dictionary.keySet());

        for (String textToFind : dictionary.keySet()) {
//            Log.i(tag, "Now replacing: " + textToFind + ", " + dictionary.get(textToFind));
            text = text.replaceAll("(?i)" + textToFind, Objects.requireNonNull(dictionary.get(textToFind))); // (?i) to ignore case
        }

        text = replaceUpper(text);

        text = text.substring(0, text.length() - 1); // Remove the space we added at the end
//        Log.i(tag, "After conversion, text is: " + text);

        textView.setText(text);

        Toast.makeText(getApplicationContext(),
                R.string.conversion_completed,
                Toast.LENGTH_SHORT)
                .show();
    }

    // Remove the UPPER and make the next letter uppercase
    public static String replaceUpper(String text) {
        String[] parts = text.split("UPPER"); // Remove upper and split right on that point

        // Loop through the parts, last one not needed as it's the end of the sentence
        for (int i = 0; i < parts.length; i++) {
            // Make first letter uppercase
            parts[i] = parts[i].substring(0, 1).toUpperCase() + parts[i].substring(1);
        }
        return TextUtils.join("", parts); // join without spacing
    }

    public void copyText() {
        TextView textView = findViewById(R.id.main_text);
        String text = textView.getText().toString();

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // Label is an indication of what the text is
        ClipData clip = ClipData.newPlainText("Text converted with AutoMark", text);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(getApplicationContext(),
                R.string.copied_to_clipboard,
                Toast.LENGTH_LONG)
                .show();
    }

    public void load() {
        Intent intent = new Intent();
        // Load a text file
        intent.setType("text/plain");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select file"), 1);
    }

    public void save() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Save as text file
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "AutoMark.txt");
        startActivityForResult(intent, 2);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        // LOAD FILE
        if(requestCode==1 && resultCode==RESULT_OK) {
            // The intent's data is the URI, which will be used to find the file
            Uri selectedFileUri = intent.getData();
            try {
                // Open the URI
                InputStream inputStream = getContentResolver().openInputStream(selectedFileUri);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                StringBuilder stringBuilder = null;

                // Combine all lines
                String line;
                while ((line = reader.readLine()) != null) {
                    // Add new line only if it's not the first
                    if (stringBuilder == null) {
                        stringBuilder = new StringBuilder();
                    } else {
                        stringBuilder.append("\n");
                    }
                    stringBuilder.append(line);
                }

                // Applu to TextView
                TextView textView = findViewById(R.id.main_text);
                textView.setText(stringBuilder.toString());
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(),
                        R.string.load_error,
                        Toast.LENGTH_SHORT)
                        .show();
                e.printStackTrace();
            }
        // SAVE FILE
        } else if (requestCode == 2 && resultCode == RESULT_OK) {
            Uri uri = intent.getData();

            try {
                // OutputStream is used to save the file
                OutputStream output = getContentResolver().openOutputStream(uri);

                TextView textView = findViewById(R.id.main_text);
                // Must get the bytes of the text and encode in UTF-8, which will be used for decoding later
                output.write(textView.getText().toString().getBytes(StandardCharsets.UTF_8));

                output.flush();
                output.close();
            }
            catch(IOException e) {
                Toast.makeText(getApplicationContext(),
                        R.string.save_error,
                        Toast.LENGTH_SHORT)
                        .show();
                e.printStackTrace();
            }
        }
    }

    public void share() {
        TextView textView = findViewById(R.id.main_text);
        String text = textView.getText().toString();

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        // Only includes text
        sendIntent.setType("text/plain");

        // No need to add a title
        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);
    }
}