package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: PairedDevicesDialog.java
// WHAT:  A pop-up window (dialog) that shows ALL of your paired TV devices
//        at once. On the discovery screen only the first 3 devices are
//        shown; this dialog appears when you tap "Show All" so you can see
//        every device. You can tap a device to connect to it, or delete a
//        device using the delete button next to it.
// ============================================================================

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.model.PairedDevice;
import com.mctrash692.freemote.model.TvDevice;
import com.mctrash692.freemote.util.IconMapper;
import com.mctrash692.freemote.util.PairedDevicesManager;

import java.util.List;

// ==========================================================================
// SECTION: PAIRED DEVICES DIALOG
// WHAT:  A pop-up window that shows every TV you have paired with the app.
//        You can tap a device to connect to it or tap the delete button
//        to remove a device from your paired list.
// ==========================================================================

public class PairedDevicesDialog extends DialogFragment {
    
    private List<PairedDevice> devices;
    private PairedDevicesManager pairedDevicesManager;
    private OnDeviceSelectedListener listener;
    private Context activityContext;
    
    // ==========================================================================
    // INTERFACE: OnDeviceSelectedListener
    // WHAT:  The communication channel back to the screen that opened this
    //        dialog. When you tap a device or delete one, these methods
    //        notify the parent screen so it can update its list.
    // ==========================================================================
    
    public interface OnDeviceSelectedListener {
        void onDeviceSelected(TvDevice device);
        void onDeviceRemoved();
    }
    
    // ==========================================================================
    // METHOD: newInstance
    // WHAT:  Creates a new instance of this dialog. Use this instead of the
    //        constructor so Android can properly manage the dialog's state.
    // ==========================================================================
    
    public static PairedDevicesDialog newInstance() {
        PairedDevicesDialog dialog = new PairedDevicesDialog();
        Bundle args = new Bundle();
        dialog.setArguments(args);
        return dialog;
    }
    
    // ==========================================================================
    // METHOD: onAttach
    // WHAT:  Runs when the dialog attaches to the screen that opened it.
    //        Sets up access to the device storage and remembers the parent
    //        screen so it can send notifications back to it.
    // ==========================================================================
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activityContext = context;
        if (context instanceof OnDeviceSelectedListener) {
            listener = (OnDeviceSelectedListener) context;
        }
        pairedDevicesManager = new PairedDevicesManager(context);
    }
    
    // ==========================================================================
    // METHOD: onCreateDialog
    // WHAT:  Runs when Android builds the dialog. Loads all paired devices
    //        from storage, creates a scrollable list, and sets up what
    //        happens when you tap a device or delete one.
    // ==========================================================================
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        devices = pairedDevicesManager.getAllDevices();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);
        builder.setTitle("Paired Devices (" + devices.size() + ")");
        
        View view = LayoutInflater.from(activityContext).inflate(R.layout.dialog_paired_devices, null);
        RecyclerView recyclerView = view.findViewById(R.id.rvPairedDevicesFull);
        recyclerView.setLayoutManager(new LinearLayoutManager(activityContext));
        recyclerView.setAdapter(new PairedDevicesAdapter(devices, pairedDevicesManager, new PairedDevicesAdapter.Callback() {
            @Override
            public void onDeviceSelected(PairedDevice device) {
                dismiss();
                if (listener != null) {
                    listener.onDeviceSelected(device.toTvDevice());
                }
            }
            
            @Override
            public void onDeviceRemoved() {
                devices = pairedDevicesManager.getAllDevices();
                recyclerView.setAdapter(new PairedDevicesAdapter(devices, pairedDevicesManager, this));
                if (listener != null) {
                    listener.onDeviceRemoved();
                }
            }
        }));
        
        builder.setView(view);
        builder.setNegativeButton("Close", (dialog, which) -> dismiss());
        
        return builder.create();
    }
    
    // ==========================================================================
    // SECTION: DEVICE LIST ADAPTER
    // WHAT:  Converts the list of paired devices into row items that the
    //        scrollable list can show. Each row has the device name, type,
    //        IP address, an icon, and a delete button.
    // ==========================================================================
    
    static class PairedDevicesAdapter extends RecyclerView.Adapter<PairedDevicesAdapter.ViewHolder> {
        
        // ==========================================================================
        // INTERFACE: Callback
        // WHAT:  Lets the adapter tell the dialog when a device is tapped
        //        or deleted so the dialog can respond.
        // ==========================================================================
        
        interface Callback {
            void onDeviceSelected(PairedDevice device);
            void onDeviceRemoved();
        }
        
        private final List<PairedDevice> devices;
        private final PairedDevicesManager manager;
        private final Callback callback;
        
        PairedDevicesAdapter(List<PairedDevice> devices, PairedDevicesManager manager, Callback callback) {
            this.devices = devices;
            this.manager = manager;
            this.callback = callback;
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
            
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(v.getContext())
                        .setTitle("Forget Device")
                        .setMessage("Remove " + device.getName() + " from paired devices?")
                        .setPositiveButton("Forget", (d, w) -> {
                            manager.removeDevice(device.getDeviceId());
                            devices.remove(position);
                            notifyItemRemoved(position);
                            if (callback != null) {
                                callback.onDeviceRemoved();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            
            holder.itemView.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onDeviceSelected(device);
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return devices.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, detail;
            ImageView icon;
            ImageView btnDelete;
            
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
