package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: TextContentDialogFragment.java
// WHAT:  A pop-up window (dialog) that shows the contents of a text file
//        from inside the app (such as the README documentation or the
//        open-source license). It reads the file and displays it as text
//        so you can read documentation or legal information without
//        leaving the app.
// ============================================================================

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.mctrash692.freemote.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

// ==========================================================================
// SECTION: TEXT CONTENT DIALOG
// WHAT:  A pop-up window that shows the contents of a text file from
//        inside the app. Used to display things like the README
//        documentation and open-source license text.
// ==========================================================================

public class TextContentDialogFragment extends DialogFragment {

    // Argument keys for passing data into the dialog
    private static final String ARG_TITLE = "title";
    private static final String ARG_RESOURCE_ID = "resource_id";

    // ==========================================================================
    // METHOD: newInstance
    // WHAT:  Creates a new text content dialog with the given title and
    //        file to display. Call this instead of the constructor to
    //        properly pass the arguments to the dialog.
    // INPUT: title = heading shown at the top, rawResourceId = the text
    //        file to read from the app's resources folder
    // ==========================================================================

    public static TextContentDialogFragment newInstance(String title, int rawResourceId) {
        TextContentDialogFragment fragment = new TextContentDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putInt(ARG_RESOURCE_ID, rawResourceId);
        fragment.setArguments(args);
        return fragment;
    }

    // ==========================================================================
    // METHOD: onCreateDialog
    // WHAT:  Runs when Android creates the dialog window. Sets the title
    //        bar text to the title that was passed in.
    // ==========================================================================

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        String title = getArguments().getString(ARG_TITLE, "Content");
        dialog.setTitle(title);
        return dialog;
    }

    // ==========================================================================
    // METHOD: onCreateView
    // WHAT:  Runs when Android needs to draw the dialog on screen. Loads
    //        the text from the specified file and shows it in the dialog.
    // ==========================================================================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_text_content, container, false);
        
        TextView tvContent = view.findViewById(R.id.tvContent);
        
        int resourceId = getArguments().getInt(ARG_RESOURCE_ID, -1);
        if (resourceId != -1) {
            String content = loadRawTextFile(resourceId);
            tvContent.setText(content);
        } else {
            tvContent.setText("Content not available");
        }
        
        return view;
    }
    
    // ==========================================================================
    // METHOD: loadRawTextFile
    // WHAT:  Reads a text file from the app's "raw" resources folder and
    //        returns it as a single string so it can be shown on screen.
    // INPUT: resourceId = the ID of the file to read
    // ==========================================================================

    private String loadRawTextFile(int resourceId) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getResources().openRawResource(resourceId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            sb.append("Failed to load content: ").append(e.getMessage());
        }
        return sb.toString();
    }
}
