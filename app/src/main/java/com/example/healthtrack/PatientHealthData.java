package com.example.healthtrack;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;
import java.util.ArrayList;

public class PatientHealthData extends AppCompatActivity {
    private final String DEVICE_ADDRESS = "BC:B5:A2:5B:02:16";

    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID HEARTRATE_CHAR_UUID = UUID.fromString("00002101-0000-1000-8000-00805F9B34FB");
    private static final UUID OXI_CHAR_UUID = UUID.fromString("00002102-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;

    private LineChart heartChart;
    private LineChart spo2Chart;

    private LineDataSet heartRateDataSet;
    private LineDataSet oxygenDataSet;

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    TextView heartRateView;
    TextView spo2View;
    private int heartRateIndex;
    private int oxygenIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_patient_health);  // Make sure this is correct
        heartRateView = findViewById(R.id.heartRateTextView);
        spo2View = findViewById(R.id.OxiTextView);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
        heartRateIndex = 0;
        oxygenIndex = 0;
        heartChart = findViewById(R.id.heartGraph);
        spo2Chart = findViewById(R.id.spo2Graph);

        // Initialize the data sets
        heartRateDataSet = new LineDataSet(new ArrayList<>(), "Heart Rate");
        oxygenDataSet = new LineDataSet(new ArrayList<>(), "Oxygen Level");

        // Set line styles (optional)
        heartRateDataSet.setColor(getResources().getColor(R.color.black));
        oxygenDataSet.setColor(getResources().getColor(R.color.black));

        // Create LineData objects
        LineData heartRateData = new LineData(heartRateDataSet);
        LineData oxygenData = new LineData(oxygenDataSet);

        // Set data to charts
        heartChart.setData(heartRateData);
        spo2Chart.setData(oxygenData);

        // Configure the charts
        configureChart(heartChart,200);
        configureChart(spo2Chart,100);

        checkBluetoothPermissions();  // Your Bluetooth connection code
    }

    // Method to check and request Bluetooth permissions
    private void checkBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[] {
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            connectToDevice(); // Permissions are already granted, proceed with connection
        }
    }

    // Callback for handling the result of the permissions request
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted, proceed with connecting to the device
                connectToDevice();
            } else {
                // Permissions not granted, inform the user
                Toast.makeText(this, "Bluetooth permissions are required to connect.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Method to connect to the Bluetooth device
    private void connectToDevice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        }
    }

    // BluetoothGattCallback to handle GATT connection events
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BluetoothGattCallback", "Connected to GATT server.");

                // Check permission before discovering services
                if (ContextCompat.checkSelfPermission(PatientHealthData.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices(); // Start service discovery
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BluetoothGattCallback", "Disconnected from GATT server.");
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothGattCallback", "Services discovered.");

                // Get heart rate and oxygen characteristics
                BluetoothGattCharacteristic heartRateCharacteristic = gatt.getService(SERVICE_UUID).getCharacteristic(HEARTRATE_CHAR_UUID);

                gatt.setCharacteristicNotification(heartRateCharacteristic, true);

                // Write the descriptor to enable notifications
                BluetoothGattDescriptor heartRateDescriptor = heartRateCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                if (heartRateDescriptor != null) {
                    heartRateDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    Log.d("BluetoothGattCallback", "Heart Notif");
                    gatt.writeDescriptor(heartRateDescriptor); // Enable heart rate notifications
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (HEARTRATE_CHAR_UUID.equals(characteristic.getUuid())) {
                int heartRate = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                // Update the UI using runOnUiThread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Update your UI elements here
                        heartRateView.setText("Heart Rate: " + heartRate);
                        updateChart(heartChart,heartRateDataSet,heartRate,heartRateIndex);
                        heartRateIndex++;
                    }
                });
            } else if (OXI_CHAR_UUID.equals(characteristic.getUuid())) {
                int oxygenLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        spo2View.setText("O2: " + oxygenLevel);
                        updateChart(spo2Chart,oxygenDataSet,oxygenLevel,oxygenIndex);
                        oxygenIndex++;
                    }
                });
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("DescriptorWrite", "Descriptor write successful for: " + descriptor.getUuid().toString());
                BluetoothGattCharacteristic oxygenCharacteristic = gatt.getService(SERVICE_UUID).getCharacteristic(OXI_CHAR_UUID);
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

    // Configure the chart's appearance and properties
    private void configureChart(LineChart chart, float max) {
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.getDescription().setEnabled(false);
        chart.setPinchZoom(true);
        chart.getAxisRight().setEnabled(false);  // Disable right axis

        // Set the X-axis to be at the bottom and ensure it allows scrolling
        chart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setAxisMinimum(0f); // Start from 0 on X-axis

        // Set minimum and maximum range for Y-axis if needed
        chart.getAxisLeft().setAxisMinimum(0f); // Minimum Y-axis value
        chart.getAxisLeft().setAxisMaximum(max); // Maximum Y-axis value (for heart rate, adjust accordingly)
        // Enable dynamic range adjustment (optional, based on your needs)
    }
    private void updateChart(LineChart chart, LineDataSet dataSet, int value, int index) {

        Entry entry = new Entry(index, value);
        // Add a new data point to the dataset
        dataSet.addEntry(entry);
        // Notify the chart that the data has changed
        chart.notifyDataSetChanged();
        // Invalidate the chart to trigger a redraw
        chart.invalidate();
    }
}
