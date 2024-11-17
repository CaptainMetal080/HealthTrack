package com.example.healthtrack;
import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Set;
import java.util.UUID;

public class DoctorHomeScreen extends AppCompatActivity {
    private static final UUID MOBILE_SERVICE_UUID = UUID.fromString("0AC359B3-1A3B-4f8C-8378-E715F4F1B775");
    private static final UUID MOBILE_HEART_CHAR_UUID = UUID.fromString("4CE9B062-DE2D-4E36-BE57-5CE9D2F1418B");
    private static final UUID MOBILE_SPO2_CHAR_UUID = UUID.fromString("1009A25F-8934-4270-AAF6-E0D76AD669E5");

    BluetoothAdapter bluetoothAdapter;
    Set<BluetoothDevice> pairedDevices;

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private BluetoothGatt bluetoothGatt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        checkBluetoothPermissions();
    }

    private void checkBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_FINE_LOCATION // Required for Bluetooth device scanning
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            ensureBluetoothEnabled(); // Permissions are granted, proceed with enabling Bluetooth
        }
    }

    @SuppressLint("MissingPermission")
    private void ensureBluetoothEnabled() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT); // Prompt to enable Bluetooth
        } else {
            connectToDevice(); // Bluetooth is enabled, connect to the device
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ensureBluetoothEnabled();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToDevice();
            } else {
                Toast.makeText(this, "Location permission is required for Bluetooth.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        pairedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().equals("Mithusan's S20 FE")) { // Replace with the server device's name
                bluetoothGatt = device.connectGatt(this, false, gattCallback);
                return;
            }
        }
    }


    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BluetoothGatt", "Connected to GATT server.");

                // Check permission before discovering services
                if (ContextCompat.checkSelfPermission(DoctorHomeScreen.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices(); // Start service discovery
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BluetoothGatt", "Disconnected from GATT server.");
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BluetoothGatt", "Services discovered.");
                BluetoothGattCharacteristic heartRateChar = gatt.getService(MOBILE_SERVICE_UUID).getCharacteristic(MOBILE_HEART_CHAR_UUID);

                // Enable notifications or read characteristic
                gatt.setCharacteristicNotification(heartRateChar, true);

                // Write the descriptor to enable notifications
                BluetoothGattDescriptor heartRateDescriptor = heartRateChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                if (heartRateDescriptor != null) {
                    heartRateDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    Log.d("BluetoothGattCallback", "Heart Notif");
                    gatt.writeDescriptor(heartRateDescriptor); // Enable heart rate notifications
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (MOBILE_HEART_CHAR_UUID.equals(characteristic.getUuid())) {
                int heartRate = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                Log.i("BluetoothGatt", "Heart rate value: " + heartRate);
            } else if (MOBILE_SPO2_CHAR_UUID.equals(characteristic.getUuid())) {
                int oxygenLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                Log.i("BluetoothGatt", "SpO2 value: " + oxygenLevel);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("DescriptorWrite", "Descriptor write successful for: " + descriptor.getUuid().toString());
                BluetoothGattCharacteristic oxygenCharacteristic = gatt.getService(MOBILE_SERVICE_UUID).getCharacteristic(MOBILE_SPO2_CHAR_UUID);
                gatt.setCharacteristicNotification(oxygenCharacteristic, true);

                BluetoothGattDescriptor oxygenDescriptor = oxygenCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                if (oxygenDescriptor != null) {
                    oxygenDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    Log.d("BluetoothGattCallback", "O2 Notif");
                    gatt.writeDescriptor(oxygenDescriptor); // Enable SpO2 notifications
                }
            } else {
                Log.e("DescriptorWrite", "Descriptor write failed for: " + descriptor.getUuid().toString());
            }
        }
    };


}
