package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: DeviceManagementActivity.java
// WHAT:  The Device List screen that shows all TVs you have paired with
//        the app. From here you can tap a TV to edit its settings, delete
//        a TV by pressing the delete icon, or add a new TV manually by
//        typing its IP address and port number.
// ============================================================================

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.model.PairedDevice;
import com.mctrash692.freemote.util.IconMapper;
import com.mctrash692.freemote.util.PairedDevicesManager;

import java.util.List;

// ==========================================================================
// SECTION: DEVICE MANAGEMENT SCREEN
// WHAT:  Shows all TVs you have paired with the app in a scrollable list.
//        You can tap a TV to edit its settings, delete a TV using the
//        delete button, or add a new TV manually by typing its info.
// ==========================================================================

public class DeviceManagementActivity extends BaseActivity {
    
    private PairedDevicesManager devicesManager;
    private RecyclerView rvDevices;
    private DeviceAdapter adapter;
    private List<PairedDevice> devices;
    
    // ==========================================================================
    // SECTION: SCREEN SETUP
    // WHAT:  Runs when the screen opens. Finds the list and buttons, then
    //        loads all paired devices from storage.
    // ==========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_management);
        
        devicesManager = new PairedDevicesManager(this);
        rvDevices = findViewById(R.id.rvDevices);
        
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddDevice).setOnClickListener(v -> addNewDevice());
        
        loadDevices();
    }
    
    // ==========================================================================
    // METHOD: onResume
    // WHAT:  Runs every time this screen becomes visible again (e.g., after
    //        returning from the edit screen). Refreshes the device list
    //        so any changes are shown immediately.
    // ==========================================================================

    @Override
    protected void onResume() {
        super.onResume();
        loadDevices();
    }
    
    // ==========================================================================
    // METHOD: loadDevices
    // WHAT:  Reads all paired devices from storage and shows them in the
    //        list. If the list adapter hasn't been created yet, it creates
    //        one. Otherwise it just updates the existing list.
    // ==========================================================================

    private void loadDevices() {
        devices = devicesManager.getAllDevices();
        if (adapter == null) {
            adapter = new DeviceAdapter(devices);
            rvDevices.setLayoutManager(new LinearLayoutManager(this));
            rvDevices.setAdapter(adapter);
        } else {
            adapter.updateDevices(devices);
        }
    }
    
    // ==========================================================================
    // METHOD: addNewDevice
    // WHAT:  Starts the process of adding a new TV manually. Opens a dialog
    //        where you can type the TV's IP address, port, and a name.
    // ==========================================================================

    private void addNewDevice() {
        // Open manual entry dialog (reuse from DeviceDiscoveryActivity)
        showManualEntryDialog();
    }
    
    // ==========================================================================
    // METHOD: showManualEntryDialog
    // WHAT:  Shows a pop-up dialog with text fields for IP address, port
    //        number, and an optional device name. When you tap "Add", it
    //        validates the input and saves the new device.
    // ==========================================================================

    private void showManualEntryDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);
        
        android.widget.EditText etIp = new android.widget.EditText(this);
        etIp.setHint("IP Address (e.g., 192.168.0.64)");
        layout.addView(etIp);
        
        android.widget.EditText etPort = new android.widget.EditText(this);
        etPort.setHint("Port (8001 or 8002)");
        etPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPort.setText("8002");
        layout.addView(etPort);
        
        android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("Device Name (optional)");
        layout.addView(etName);
        
        new AlertDialog.Builder(this)
            .setTitle("Add Device Manually")
            .setView(layout)
            .setPositiveButton("Add", (d, w) -> {
                String ip = etIp.getText().toString().trim();
                String portStr = etPort.getText().toString().trim();
                String name = etName.getText().toString().trim();
                
                if (ip.isEmpty()) {
                    Toast.makeText(this, "IP address required", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                int port;
                try {
                    port = Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (name.isEmpty()) {
                    name = "Manual " + ip;
                }
                
                PairedDevice device = new PairedDevice();
                device.setName(name);
                device.setIpAddress(ip);
                device.setPort(port);
                device.setType(com.mctrash692.freemote.model.TvDevice.Type.SAMSUNG);
                
                devicesManager.saveDevice(device);
                loadDevices();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    // ==========================================================================
    // METHOD: editDevice
    // WHAT:  Opens the Edit Device screen for the given TV so you can
    //        change its name, IP, type, and other settings.
    // INPUT: device = the TV to edit
    // ==========================================================================

    private void editDevice(PairedDevice device) {
        Intent intent = new Intent(this, DeviceEditActivity.class);
        intent.putExtra("device_id", device.getDeviceId());
        startActivity(intent);
    }
    
    // ==========================================================================
    // METHOD: deleteDevice
    // WHAT:  Shows a confirmation dialog asking if you want to remove this
    //        TV from your paired list. If you confirm, it deletes the
    //        device and refreshes the list.
    // INPUT: device = the TV to delete
    // ==========================================================================

    private void deleteDevice(PairedDevice device) {
        new AlertDialog.Builder(this)
            .setTitle("Forget Device")
            .setMessage("Remove " + device.getName() + " from paired devices?")
            .setPositiveButton("Forget", (d, w) -> {
                devicesManager.removeDevice(device.getDeviceId());
                loadDevices();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    // ==========================================================================
    // SECTION: DEVICE LIST ADAPTER
    // WHAT:  Converts the list of paired devices into row items that the
    //        scrollable list can display. Each row shows the device name,
    //        type, IP address, an icon, and a delete button.
    // ==========================================================================

    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        private List<PairedDevice> devices;
        
        DeviceAdapter(List<PairedDevice> devices) {
            this.devices = devices;
        }
        
        // ======================================================================
        // METHOD: updateDevices
        // WHAT:  Replaces the list of devices and refreshes the display.
        // ======================================================================

        void updateDevices(List<PairedDevice> devices) {
            this.devices = devices;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_device, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PairedDevice device = devices.get(position);
            holder.name.setText(device.getName());
            holder.detail.setText(device.getType().name() + "  •  " + device.getIpAddress() + ":" + device.getPort());
            holder.icon.setImageResource(IconMapper.getIconForDevice(device.toTvDevice()));
            
            holder.itemView.setOnClickListener(v -> editDevice(device));
            holder.btnDelete.setOnClickListener(v -> deleteDevice(device));
        }
        
        @Override
        public int getItemCount() {
            return devices.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, detail;
            ImageView icon;
            ImageButton btnDelete;
            
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.tvDeviceName);
                detail = v.findViewById(R.id.tvDeviceDetail);
                icon = v.findViewById(R.id.ivDeviceIcon);
                
                // Add delete button to item_device layout if not present
                btnDelete = v.findViewById(R.id.btnDelete);
                if (btnDelete == null) {
                    // Create delete button programmatically if not in layout
                    setupDeleteButton(v);
                }
            }
            
            // ==================================================================
            // METHOD: setupDeleteButton
            // WHAT:  Creates a delete button from scratch and adds it to the
            //        device row, in case the layout doesn't already have one.
            // ==================================================================

            private void setupDeleteButton(View v) {
                btnDelete = new ImageButton(v.getContext());
                btnDelete.setImageResource(android.R.drawable.ic_menu_delete);
                btnDelete.setBackground(null);
                btnDelete.setPadding(16, 16, 16, 16);
                if (v instanceof android.widget.LinearLayout) {
                    android.widget.LinearLayout parent = (android.widget.LinearLayout) v;
                    parent.addView(btnDelete);
                }
            }
        }
    }
}
