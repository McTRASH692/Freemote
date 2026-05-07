package com.mctrash692.freemote.ui;

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

public class TextContentDialogFragment extends DialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_RESOURCE_ID = "resource_id";

    public static TextContentDialogFragment newInstance(String title, int rawResourceId) {
        TextContentDialogFragment fragment = new TextContentDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putInt(ARG_RESOURCE_ID, rawResourceId);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        String title = getArguments().getString(ARG_TITLE, "Content");
        dialog.setTitle(title);
        return dialog;
    }

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
    
    private String loadRawTextFile(int resourceId) {
        StringBuilder sb = new StringBuilder();
        try {
            InputStream is = getResources().openRawResource(resourceId);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            is.close();
        } catch (Exception e) {
            sb.append("Failed to load content: ").append(e.getMessage());
        }
        return sb.toString();
    }
}
