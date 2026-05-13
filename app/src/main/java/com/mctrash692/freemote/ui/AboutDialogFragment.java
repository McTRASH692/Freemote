package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: AboutDialogFragment.java
// WHAT:  The "About Freemote" pop-up window that tells you about the app.
//        From here you can read the README file, view the open-source
//        license, open the GitHub repository in your browser, visit the
//        developer's GitHub profile, or send an email with feedback.
// ============================================================================

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

// ==========================================================================
// SECTION: ABOUT DIALOG
// WHAT:  A pop-up window that shows information about the Freemote app.
//        You can read the README, view the license, open the GitHub page,
//        visit the developer's profile, or send an email.
// ==========================================================================

public class AboutDialogFragment extends DialogFragment {

    // Links and contact information for the About screen
    private static final String GITHUB_REPO_URL = "https://github.com/McTRASH692/Freemote";
    private static final String DEV_GITHUB_URL = "https://github.com/McTRASH692";
    private static final String DEV_EMAIL = "rgardnerthompson@gmail.com";

    // ==========================================================================
    // METHOD: onCreateDialog
    // WHAT:  Runs when Android creates the dialog. Sets the title bar to
    //        "About Freemote".
    // ==========================================================================

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle("About Freemote");
        return dialog;
    }

    // ==========================================================================
    // METHOD: onCreateView
    // WHAT:  Runs when Android needs to draw the dialog. Finds the clickable
    //        links on screen and sets them up so they respond when tapped.
    // ==========================================================================

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
    
    // ==========================================================================
    // METHOD: showReadme
    // WHAT:  Opens the README file in a text pop-up dialog so you can read
    //        the app's documentation.
    // ==========================================================================
    
    private void showReadme() {
        TextContentDialogFragment dialog = TextContentDialogFragment.newInstance("README.md", R.raw.readme);
        dialog.show(getChildFragmentManager(), "readme_dialog");
    }
    
    // ==========================================================================
    // METHOD: showLicense
    // WHAT:  Opens the open-source license file in a text pop-up dialog.
    // ==========================================================================
    
    private void showLicense() {
        TextContentDialogFragment dialog = TextContentDialogFragment.newInstance("LICENSE", R.raw.license);
        dialog.show(getChildFragmentManager(), "license_dialog");
    }
    
    // ==========================================================================
    // METHOD: openUrl
    // WHAT:  Opens a web link in your phone's browser.
    // INPUT: url = the web address to open (e.g., the GitHub repo)
    // ==========================================================================
    
    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
    
    // ==========================================================================
    // METHOD: sendEmail
    // WHAT:  Opens your phone's email app with a new message addressed to
    //        the developer, ready for you to write feedback.
    // ==========================================================================
    
    private void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + DEV_EMAIL));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Freemote App Feedback");
        intent.putExtra(Intent.EXTRA_TEXT, "Hello,\n\n");
        startActivity(intent);
    }
}
