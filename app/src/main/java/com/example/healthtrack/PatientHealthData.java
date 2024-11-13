package com.example.healthtrack;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.UUID;

public class PatientHealthData extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mDevice;
    private TextView heartRateTextView;
    private Button connectButton;

    // Replace with your actual BLE device's MAC address
    private final String DEVICE_ADDRESS = "bc:b5:a2:5b:02:16"; // Example: Your BLE device MAC address

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_health);

        // Find views
        heartRateTextView = findViewById(R.id.heartRateTextView);
        connectButton = findViewById(R.id.connect);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth not available or not enabled", Toast.LENGTH_SHORT).show();
            return;
        }

        // Initialize BluetoothLeScanner for BLE scanning
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // Start scanning for BLE devices
        startScan();

    }

    // Start scanning for BLE devices
    private void startScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }

        // Start scanning for BLE devices
        mBluetoothLeScanner.startScan(scanCallback);
    }

    // Scan callback to handle found devices
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();

            // Check if the device address matches the target device
            if (device.getAddress().equals(DEVICE_ADDRESS)) {
                // Stop scanning once we find the device
                if (ActivityCompat.checkSelfPermission(PatientHealthData.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                mBluetoothLeScanner.stopScan(scanCallback);

                // Proceed to connect to the device
                connectToBluetoothDevice(device);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(PatientHealthData.this, "BLE Scan failed with error code: " + errorCode, Toast.LENGTH_SHORT).show();
        }
    };

    // Connect to the selected Bluetooth device
    private void connectToBluetoothDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mBluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    // We are connected to the BLE device
                    Toast.makeText(PatientHealthData.this, "Connected to BLE Device", Toast.LENGTH_SHORT).show();
                    // Discover services after connecting
                    if (ActivityCompat.checkSelfPermission(PatientHealthData.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    mBluetoothGatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Toast.makeText(PatientHealthData.this, "Disconnected from BLE Device", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // You can access the services and characteristics here
                    // For example, you can read heart rate data from a specific service and characteristic
                    readHeartRateData(gatt);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, android.bluetooth.BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Handle the heart rate data (for example)
                    byte[] data = characteristic.getValue();
                    String heartRate = new String(data);
                    // Update the UI with the heart rate data
                    runOnUiThread(() -> heartRateTextView.setText("Heart Rate: " + heartRate + " BPM"));
                }
            }
        });
    }

    // Method to read heart rate data from the device
    private void readHeartRateData(BluetoothGatt gatt) {
        // Replace with the actual service and characteristic UUIDs for heart rate data
        // UUID heartRateServiceUUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
        // UUID heartRateMeasurementUUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

        // Example UUIDs for heart rate service and measurement (replace with actual ones for your device)
        UUID heartRateServiceUUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
        UUID heartRateMeasurementUUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

        android.bluetooth.BluetoothGattService heartRateService = gatt.getService(heartRateServiceUUID);
        if (heartRateService != null) {
            android.bluetooth.BluetoothGattCharacteristic characteristic = heartRateService.getCharacteristic(heartRateMeasurementUUID);
            if (characteristic != null) {
                // Read the characteristic value
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                gatt.readCharacteristic(characteristic);
            }
        }
    }

    // Handle the result of permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) { // Check if this is the permission request for Bluetooth Connect
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with scanning and connection
                startScan();
            } else {
                // Permission denied, inform the user
                Toast.makeText(this, "Bluetooth permission is required to connect to the device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            mBluetoothGatt.close();
        }
    }
}
