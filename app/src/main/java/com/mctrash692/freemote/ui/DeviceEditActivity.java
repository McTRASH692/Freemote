package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: DeviceEditActivity.java
// WHAT:  The Edit Device screen where you can change a TV's settings.
//        From here you can rename the device, change its IP address and
//        port, select the TV brand/type, set its MAC address (needed for
//        Wake-on-LAN to turn it on), adjust the touchpad sensitivity, and
//        choose whether the app should auto-connect to this TV. You can
//        also forget (delete) the device from here.
// ============================================================================

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.model.PairedDevice;
import com.mctrash692.freemote.model.TvDevice;
import com.mctrash692.freemote.util.PairedDevicesManager;

// ==========================================================================
// SECTION: DEVICE EDIT SCREEN
// WHAT:  Lets you change the settings for a specific TV. You can edit the
//        device name, IP address, port number, TV brand/type, MAC address
//        (for Wake-on-LAN), touchpad sensitivity, and auto-connect option.
//        You can also delete (forget) the device from here.
// ==========================================================================

public class DeviceEditActivity extends BaseActivity {
    
    private PairedDevicesManager devicesManager;
    private PairedDevice device;
    private String deviceId;
    
    // All the input fields and controls on this screen
    private EditText etDeviceName;
    private EditText etIpAddress;
    private EditText etPort;
    private Spinner spinnerDeviceType;
    private EditText etMacAddress;
    private SeekBar seekSensitivity;
    private TextView tvSensitivityValue;
    private Switch switchAutoConnect;
    private Button btnSave;
    private Button btnForget;
    
    // ==========================================================================
    // SECTION: SCREEN SETUP
    // WHAT:  Runs when the Edit Device screen opens. Looks up the device
    //        from storage, finds all the input fields, fills them with the
    //        current device data, and connects the Save/Forget buttons.
    // ==========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_edit);
        
        devicesManager = new PairedDevicesManager(this);
        deviceId = getIntent().getStringExtra("device_id");
        
        // If no device ID was passed, show an error and close
        if (deviceId == null) {
            Toast.makeText(this, "Error: No device specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Try to find the device in storage
        device = devicesManager.getDeviceById(deviceId);
        if (device == null) {
            Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        initViews();
        loadDeviceData();
        setupListeners();
    }
    
    // ==========================================================================
    // METHOD: initViews
    // WHAT:  Finds every input field, dropdown, slider, and button on the
    //        screen and connects them so they respond to your actions.
    // ==========================================================================

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        etDeviceName = findViewById(R.id.etDeviceName);
        etIpAddress = findViewById(R.id.etIpAddress);
        etPort = findViewById(R.id.etPort);
        spinnerDeviceType = findViewById(R.id.spinnerDeviceType);
        etMacAddress = findViewById(R.id.etMacAddress);
        seekSensitivity = findViewById(R.id.seekSensitivity);
        tvSensitivityValue = findViewById(R.id.tvSensitivityValue);
        switchAutoConnect = findViewById(R.id.switchAutoConnect);
        btnSave = findViewById(R.id.btnSave);
        btnForget = findViewById(R.id.btnForget);
        
        // Setup device type spinner with all supported TV brands
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"SAMSUNG", "ANDROID_TV", "LG", "ROKU", "PANASONIC", "PHILIPS", "SONY", "TCL", "HISENSE", "SHARP", "HAIER", "VIZIO", "APPLE_TV", "FIRE_TV", "XIAOMI", "UNKNOWN"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDeviceType.setAdapter(typeAdapter);
        
        // Sensitivity seekbar (0-40, matching PairedDevice.setTouchpadSensitivity clamp)
        seekSensitivity.setMax(40);
        seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = 0.5f + (progress / 10.0f);
                tvSensitivityValue.setText(String.format("%.1fx", value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    // ==========================================================================
    // METHOD: loadDeviceData
    // WHAT:  Fills all the input fields with the current settings from the
    //        device that was loaded from storage.
    // ==========================================================================

    private void loadDeviceData() {
        etDeviceName.setText(device.getName());
        etIpAddress.setText(device.getIpAddress());
        etPort.setText(String.valueOf(device.getPort()));
        
        int typeCount = spinnerDeviceType.getAdapter().getCount();
        for (int i = 0; i < typeCount; i++) {
            if (spinnerDeviceType.getAdapter().getItem(i).toString().equals(device.getType().name())) {
                spinnerDeviceType.setSelection(i);
                break;
            }
        }
        
        if (device.getMacAddress() != null) {
            etMacAddress.setText(device.getMacAddress());
        }
        
        int sensitivity = device.getTouchpadSensitivity();
        seekSensitivity.setProgress(sensitivity);
        float value = 0.5f + (sensitivity / 10.0f);
        tvSensitivityValue.setText(String.format("%.1fx", value));
        
        switchAutoConnect.setChecked(device.isAutoConnect());
    }
    
    // ==========================================================================
    // METHOD: setupListeners
    // WHAT:  Connects the Save and Forget buttons to their actions.
    // ==========================================================================

    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveDevice());
        btnForget.setOnClickListener(v -> confirmForget());
    }
    
    // ==========================================================================
    // METHOD: saveDevice
    // WHAT:  Reads all the values from the input fields, validates them,
    //        and saves the updated device to storage. Then closes the
    //        screen and returns to the device list.
    // ==========================================================================

    private void saveDevice() {
        String name = etDeviceName.getText().toString().trim();
        String ip = etIpAddress.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String mac = etMacAddress.getText().toString().trim().toUpperCase();
        
        if (name.isEmpty()) {
            etDeviceName.setError("Device name required");
            return;
        }
        
        if (ip.isEmpty()) {
            etIpAddress.setError("IP address required");
            return;
        }
        
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            etPort.setError("Valid port required (1-65535)");
            return;
        }
        
        // Update device
        device.setName(name);
        device.setIpAddress(ip);
        device.setPort(port);
        
        String selectedType = (String) spinnerDeviceType.getSelectedItem();
        device.setType(TvDevice.Type.valueOf(selectedType));
        
        if (!mac.isEmpty()) {
            device.setMacAddress(mac);
        }
        
        device.setTouchpadSensitivity(seekSensitivity.getProgress());
        device.setAutoConnect(switchAutoConnect.isChecked());
        
        devicesManager.saveDevice(device);
        
        finish();
    }
    
    // ==========================================================================
    // METHOD: confirmForget
    // WHAT:  Shows a confirmation dialog. If you confirm, it deletes the
    //        device from storage and closes the screen.
    // ==========================================================================

    private void confirmForget() {
        new AlertDialog.Builder(this)
            .setTitle("Forget Device")
            .setMessage("Remove " + device.getName() + " from paired devices?")
            .setPositiveButton("Forget", (d, w) -> {
                devicesManager.removeDevice(device.getDeviceId());
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
