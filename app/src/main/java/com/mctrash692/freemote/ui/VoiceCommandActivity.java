package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: VoiceCommandActivity.java
// WHAT:  The Voice Command screen. When you tap the microphone button on
//        the remote, this screen opens and listens to what you say using
//        Android's built-in speech recognition. It turns your spoken words
//        into text and sends that text to the remote control screen, which
//        then types it on your TV (for example, to search for a show).
//        This screen opens and closes automatically — you barely see it.
// ============================================================================

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Locale;

// ==========================================================================
// SECTION: VOICE COMMAND SCREEN
// WHAT:  This screen listens to what you say and sends the spoken words
//        as text to the remote control screen, which types them on your
//        TV. It opens automatically when you tap the microphone button
//        and closes as soon as the voice is processed.
// ==========================================================================

public class VoiceCommandActivity extends BaseActivity {

    // Code used to identify the voice recognition result when it comes back
    private static final int VOICE_REQUEST_CODE = 100;

    // ==========================================================================
    // METHOD: onCreate
    // WHAT:  Runs when the voice command screen opens. Immediately starts
    //        Android's built-in speech recognizer and shows a prompt
    //        asking you to "Say a command for your TV". If speech
    //        recognition is not available on this device, it closes.
    // ==========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command for your TV");

        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // ==========================================================================
    // METHOD: onActivityResult
    // WHAT:  Runs when speech recognition finishes. Gets the spoken words
    //        as text and sends them to the remote control screen. Then
    //        closes this screen automatically.
    // ==========================================================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                processVoiceCommand(results.get(0));
            }
        }
        finish();
    }

    // ==========================================================================
    // METHOD: processVoiceCommand
    // WHAT:  Takes the spoken words and sends them to the remote control
    //        screen via a local broadcast (a message sent within the app).
    //        The remote screen receives this and types the words on the TV.
    // INPUT: command = what the user said, as text
    // NOTE:  Uses LocalBroadcastManager, not a system-wide broadcast,
    //        so only the RemoteActivity receives the message.
    // ==========================================================================

    private void processVoiceCommand(String command) {
        // Must use LocalBroadcastManager — RemoteActivity registers with it,
        // not with the system-wide broadcast bus.
        Intent broadcastIntent = new Intent("com.mctrash692.freemote.VOICE_COMMAND");
        broadcastIntent.putExtra("command", command);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent);
    }
}
