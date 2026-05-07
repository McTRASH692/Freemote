package com.mctrash692.freemote.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.databinding.ActivityDeviceDiscoveryBinding;
import com.mctrash692.freemote.model.PairedDevice;
import com.mctrash692.freemote.model.TvDevice;
import com.mctrash692.freemote.util.IconMapper;
import com.mctrash692.freemote.util.NetworkScanner;
import com.mctrash692.freemote.util.NsdHelper;
import com.mctrash692.freemote.util.PairedDevicesManager;
import com.mctrash692.freemote.util.SsdpDiscovery;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DeviceDiscoveryActivity extends BaseActivity implements PairedDevicesDialog.OnDeviceSelectedListener {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private ActivityDeviceDiscoveryBinding binding;
    private NsdHelper nsdHelper;
    private SsdpDiscovery ssdpDiscovery;
    private NetworkScanner networkScanner;
    private DeviceAdapter discoveredAdapter;
    private PairedDeviceAdapter pairedAdapter;
    private final List<TvDevice> discoveredDevices = new ArrayList<>();
    private List<PairedDevice> pairedDevices = new ArrayList<>();
    private final Map<String, TvDevice> uniqueDevices = new HashMap<>();
    private PairedDevicesManager pairedDevicesManager;
    private Button btnShowAllPaired;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceDiscoveryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        pairedDevicesManager = new PairedDevicesManager(this);
        btnShowAllPaired = findViewById(R.id.btnShowAllPaired);

        discoveredAdapter = new DeviceAdapter(discoveredDevices, this::onDeviceSelected);
        pairedAdapter = new PairedDeviceAdapter(pairedDevices, this::onPairedDeviceSelected, this::onPairedDeviceLongPress);

        binding.rvDevices.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDevices.setAdapter(discoveredAdapter);

        RecyclerView rvPaired = findViewById(R.id.rvPairedDevices);
        rvPaired.setLayoutManager(new LinearLayoutManager(this));
        rvPaired.setAdapter(pairedAdapter);

        loadPairedDevices();

        btnShowAllPaired.setOnClickListener(v -> showAllPairedDevices());

        checkAndRequestPermissions();

        binding.btnManualEntry.setOnClickListener(v -> showManualEntryDialog());
        binding.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void loadPairedDevices() {
        pairedDevices.clear();
        pairedDevices.addAll(pairedDevicesManager.getAllDevices());
        
        if (pairedDevices.size() > 3) {
            btnShowAllPaired.setVisibility(View.VISIBLE);
            btnShowAllPaired.setText("Show All (" + pairedDevices.size() + ")");
            List<PairedDevice> limited = new ArrayList<>(pairedDevices.subList(0, 3));
            pairedDevices.clear();
            pairedDevices.addAll(limited);
        } else {
            btnShowAllPaired.setVisibility(View.GONE);
        }
        pairedAdapter.notifyDataSetChanged();
    }

    private void refreshPairedList() {
        loadPairedDevices();
    }

    private void showAllPairedDevices() {
        PairedDevicesDialog dialog = PairedDevicesDialog.newInstance(null);
        dialog.show(getSupportFragmentManager(), "paired_devices");
    }

    @Override
    public void onDeviceSelected(TvDevice device) {
        onDeviceSelectedInternal(device);
    }

    @Override
    public void onDeviceRemoved() {
        refreshPairedList();
    }

    private void onPairedDeviceSelected(PairedDevice device) {
        device.updateLastUsed();
        pairedDevicesManager.saveDevice(device);
        
        Intent intent = new Intent(this, RemoteActivity.class);
        intent.putExtra(RemoteActivity.EXTRA_DEVICE_ID, device.getDeviceId());
        intent.putExtra(RemoteActivity.EXTRA_IP, device.getIpAddress());
        intent.putExtra(RemoteActivity.EXTRA_PORT, device.getPort());
        intent.putExtra(RemoteActivity.EXTRA_TYPE, device.getType().name());
        intent.putExtra(RemoteActivity.EXTRA_NAME, device.getName());
        intent.putExtra(RemoteActivity.EXTRA_MAC, device.getMacAddress());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void onPairedDeviceLongPress(PairedDevice device) {
        String[] options = {"Edit Device", "Forget Device"};
        new AlertDialog.Builder(this)
                .setTitle(device.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, DeviceEditActivity.class);
                        intent.putExtra("device_id", device.getDeviceId());
                        startActivity(intent);
                    } else if (which == 1) {
                        new AlertDialog.Builder(this)
                                .setTitle("Forget Device")
                                .setMessage("Remove " + device.getName() + " from paired devices?")
                                .setPositiveButton("Forget", (d, w) -> {
                                    pairedDevicesManager.removeDevice(device.getDeviceId());
                                    refreshPairedList();
                                    Toast.makeText(this, "Device removed", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            startDiscovery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Some permissions denied. App may have limited functionality.", Toast.LENGTH_LONG).show();
            }
            startDiscovery();
        }
    }

    private void startDiscovery() {
        Consumer<TvDevice> deviceCallback = device -> runOnUiThread(() -> {
            String ipKey = device.getIpAddress();
            if (!uniqueDevices.containsKey(ipKey)) {
                uniqueDevices.put(ipKey, device);
                discoveredDevices.add(device);
                discoveredAdapter.notifyItemInserted(discoveredDevices.size() - 1);
                binding.tvStatus.setText(discoveredDevices.size() + " device(s) found");
            }
        });

        nsdHelper = new NsdHelper(this, deviceCallback);
        nsdHelper.startDiscovery();

        ssdpDiscovery = new SsdpDiscovery(deviceCallback);
        ssdpDiscovery.start();

        String subnet = getLocalSubnet();
        if (subnet != null) {
            networkScanner = new NetworkScanner(deviceCallback);
            binding.getRoot().postDelayed(() -> {
                binding.tvStatus.setText("Scanning network...");
                networkScanner.scanNetwork(subnet);
            }, 5000);
        }
    }

    private String getLocalSubnet() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    LinkProperties lp = cm.getLinkProperties(activeNetwork);
                    if (lp != null) {
                        for (LinkAddress la : lp.getLinkAddresses()) {
                            InetAddress addr = la.getAddress();
                            if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                                byte[] b = addr.getAddress();
                                return String.format("%d.%d.%d.", b[0] & 0xff, b[1] & 0xff, b[2] & 0xff);
                            }
                        }
                    }
                }
            } else {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                int ip = wm.getConnectionInfo().getIpAddress();
                return String.format("%d.%d.%d.", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff));
            }
        } catch (Exception e) {
            // Fall through
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (nsdHelper != null) nsdHelper.stopDiscovery();
        if (ssdpDiscovery != null) ssdpDiscovery.stop();
        if (networkScanner != null) networkScanner.stop();
    }

    private void onDeviceSelectedInternal(TvDevice device) {
        try {
            if (!pairedDevicesManager.isDeviceSaved(device.getIpAddress())) {
                PairedDevice pairedDevice = new PairedDevice(device);
                pairedDevicesManager.saveDevice(pairedDevice);
                refreshPairedList();
            } else {
                PairedDevice existing = pairedDevicesManager.getDeviceByIp(device.getIpAddress());
                if (existing != null) {
                    existing.updateLastUsed();
                    pairedDevicesManager.saveDevice(existing);
                }
            }
            
            Intent intent = new Intent(this, RemoteActivity.class);
            intent.putExtra(RemoteActivity.EXTRA_IP, device.getIpAddress());
            intent.putExtra(RemoteActivity.EXTRA_PORT, device.getPort());
            intent.putExtra(RemoteActivity.EXTRA_TYPE, device.getType().name());
            intent.putExtra(RemoteActivity.EXTRA_NAME, device.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening remote: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showManualEntryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText etIp = new EditText(this);
        etIp.setHint("IP Address (e.g., 192.168.0.64)");
        layout.addView(etIp);

        EditText etPort = new EditText(this);
        etPort.setHint("Port (8001 or 8002)");
        etPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPort.setText("8002");
        layout.addView(etPort);

        EditText etName = new EditText(this);
        etName.setHint("Device Name (optional)");
        layout.addView(etName);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Manual Connection")
                .setView(layout)
                .setPositiveButton("Connect", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            String name = etName.getText().toString().trim();

            if (ip.isEmpty()) {
                etIp.setError("IP address required");
                return;
            }
            int port;
            try {
                port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) throw new NumberFormatException("out of range");
            } catch (NumberFormatException e) {
                etPort.setError("Enter a valid port (1–65535)");
                return;
            }
            if (name.isEmpty()) {
                name = "Manual " + ip;
            }
            dialog.dismiss();
            
            TvDevice device = new TvDevice(name, ip, port, TvDevice.Type.SAMSUNG);
            onDeviceSelectedInternal(device);
        }));

        dialog.show();
    }

    static class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        interface OnClick { void onClick(TvDevice device); }
        private final List<TvDevice> items;
        private final OnClick onClick;

        DeviceAdapter(List<TvDevice> items, OnClick onClick) {
            this.items = items;
            this.onClick = onClick;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_device, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TvDevice device = items.get(position);
            holder.name.setText(device.getName());
            holder.detail.setText(device.getType().name() + "  •  " + device.getIpAddress() + ":" + device.getPort());
            holder.icon.setImageResource(IconMapper.getIconForDevice(device));
            holder.btnDelete.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> onClick.onClick(device));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, detail;
            ImageView icon;
            ImageButton btnDelete;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.tvDeviceName);
                detail = v.findViewById(R.id.tvDeviceDetail);
                icon = v.findViewById(R.id.ivDeviceIcon);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }

    class PairedDeviceAdapter extends RecyclerView.Adapter<PairedDeviceAdapter.ViewHolder> {
        interface OnClick { void onClick(PairedDevice device); }
        interface OnLongPress { void onLongPress(PairedDevice device); }
        
        private final List<PairedDevice> items;
        private final OnClick onClick;
        private final OnLongPress onLongPress;

        PairedDeviceAdapter(List<PairedDevice> items, OnClick onClick, OnLongPress onLongPress) {
            this.items = items;
            this.onClick = onClick;
            this.onLongPress = onLongPress;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_device, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PairedDevice device = items.get(position);
            holder.name.setText(device.getName());
            holder.detail.setText(device.getType().name() + "  •  " + device.getIpAddress() + ":" + device.getPort());
            holder.icon.setImageResource(IconMapper.getIconForDevice(device.toTvDevice()));
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(DeviceDiscoveryActivity.this)
                        .setTitle("Forget Device")
                        .setMessage("Remove " + device.getName() + " from paired devices?")
                        .setPositiveButton("Forget", (d, w) -> {
                            pairedDevicesManager.removeDevice(device.getDeviceId());
                            refreshPairedList();
                            Toast.makeText(DeviceDiscoveryActivity.this, "Device removed", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            holder.itemView.setOnClickListener(v -> onClick.onClick(device));
            holder.itemView.setOnLongClickListener(v -> {
                onLongPress.onLongPress(device);
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, detail;
            ImageView icon;
            ImageButton btnDelete;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.tvDeviceName);
                detail = v.findViewById(R.id.tvDeviceDetail);
                icon = v.findViewById(R.id.ivDeviceIcon);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }
}
