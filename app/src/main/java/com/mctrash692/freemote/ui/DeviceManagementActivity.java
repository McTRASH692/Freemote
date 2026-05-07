package com.mctrash692.freemote.ui;

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

public class DeviceManagementActivity extends BaseActivity {
    
    private PairedDevicesManager devicesManager;
    private RecyclerView rvDevices;
    private DeviceAdapter adapter;
    private List<PairedDevice> devices;
    
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
    
    @Override
    protected void onResume() {
        super.onResume();
        loadDevices();
    }
    
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
    
    private void addNewDevice() {
        // Open manual entry dialog (reuse from DeviceDiscoveryActivity)
        showManualEntryDialog();
    }
    
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
                Toast.makeText(this, "Device added", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void editDevice(PairedDevice device) {
        Intent intent = new Intent(this, DeviceEditActivity.class);
        intent.putExtra("device_id", device.getDeviceId());
        startActivity(intent);
    }
    
    private void deleteDevice(PairedDevice device) {
        new AlertDialog.Builder(this)
            .setTitle("Forget Device")
            .setMessage("Remove " + device.getName() + " from paired devices?")
            .setPositiveButton("Forget", (d, w) -> {
                devicesManager.removeDevice(device.getDeviceId());
                loadDevices();
                Toast.makeText(this, "Device removed", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        private List<PairedDevice> devices;
        
        DeviceAdapter(List<PairedDevice> devices) {
            this.devices = devices;
        }
        
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
        
        class ViewHolder extends RecyclerView.ViewHolder {
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
            
            private void setupDeleteButton(View v) {
                btnDelete = new ImageButton(v.getContext());
                btnDelete.setImageResource(android.R.drawable.ic_menu_delete);
                btnDelete.setBackground(null);
                btnDelete.setPadding(16, 16, 16, 16);
                android.widget.LinearLayout parent = (android.widget.LinearLayout) v;
                parent.addView(btnDelete);
            }
        }
    }
}
