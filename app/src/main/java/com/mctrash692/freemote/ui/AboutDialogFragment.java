package com.mctrash692.freemote.ui;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.mctrash692.freemote.R;

public class AboutDialogFragment extends DialogFragment {

    private static final String GITHUB_REPO_URL = "https://github.com/McTRASH692/Freemote";
    private static final String DEV_GITHUB_URL = "https://github.com/McTRASH692";
    private static final String DEV_EMAIL = "rgardnerthompson@gmail.com";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle("About Freemote");
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_about, container, false);
        
        TextView tvEmail = view.findViewById(R.id.tvEmail);
        tvEmail.setOnClickListener(v -> sendEmail());
        
        LinearLayout linkReadme = view.findViewById(R.id.linkReadme);
        LinearLayout linkLicense = view.findViewById(R.id.linkLicense);
        LinearLayout linkRepo = view.findViewById(R.id.linkRepo);
        LinearLayout linkDevGitHub = view.findViewById(R.id.linkDevGitHub);
        LinearLayout linkEmail = view.findViewById(R.id.linkEmail);
        
        linkReadme.setOnClickListener(v -> showReadme());
        linkLicense.setOnClickListener(v -> showLicense());
        linkRepo.setOnClickListener(v -> openUrl(GITHUB_REPO_URL));
        linkDevGitHub.setOnClickListener(v -> openUrl(DEV_GITHUB_URL));
        linkEmail.setOnClickListener(v -> sendEmail());
        
        return view;
    }
    
    private void showReadme() {
        TextContentDialogFragment dialog = TextContentDialogFragment.newInstance("README.md", R.raw.readme);
        dialog.show(getChildFragmentManager(), "readme_dialog");
    }
    
    private void showLicense() {
        TextContentDialogFragment dialog = TextContentDialogFragment.newInstance("LICENSE", R.raw.license);
        dialog.show(getChildFragmentManager(), "license_dialog");
    }
    
    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
    
    private void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + DEV_EMAIL));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Freemote App Feedback");
        intent.putExtra(Intent.EXTRA_TEXT, "Hello,\n\n");
        startActivity(intent);
    }
}
