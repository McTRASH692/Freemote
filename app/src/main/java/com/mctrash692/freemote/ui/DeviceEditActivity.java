package com.mctrash692.freemote.ui;

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

public class DeviceEditActivity extends BaseActivity {
    
    private PairedDevicesManager devicesManager;
    private PairedDevice device;
    private String deviceId;
    
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_edit);
        
        devicesManager = new PairedDevicesManager(this);
        deviceId = getIntent().getStringExtra("device_id");
        
        if (deviceId == null) {
            Toast.makeText(this, "Error: No device specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
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
        
        // Setup device type spinner
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,
            new String[]{"SAMSUNG", "ANDROID_TV", "UNKNOWN"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDeviceType.setAdapter(typeAdapter);
        
        // Sensitivity seekbar
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
    
    private void loadDeviceData() {
        etDeviceName.setText(device.getName());
        etIpAddress.setText(device.getIpAddress());
        etPort.setText(String.valueOf(device.getPort()));
        
        // Set spinner selection
        String typeName = device.getType().name();
        String[] types = {"SAMSUNG", "ANDROID_TV", "UNKNOWN"};
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(typeName)) {
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
    
    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveDevice());
        btnForget.setOnClickListener(v -> confirmForget());
    }
    
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
        
        Toast.makeText(this, "Device saved", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private void confirmForget() {
        new AlertDialog.Builder(this)
            .setTitle("Forget Device")
            .setMessage("Remove " + device.getName() + " from paired devices?")
            .setPositiveButton("Forget", (d, w) -> {
                devicesManager.removeDevice(device.getDeviceId());
                Toast.makeText(this, "Device removed", Toast.LENGTH_SHORT).show();
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
