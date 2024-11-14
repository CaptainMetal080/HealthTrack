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
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;

public class PatientHealthData extends AppCompatActivity {
    private final String DEVICE_ADDRESS = "BC:B5:A2:5B:02:16";

    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID HEARTRATE_CHAR_UUID = UUID.fromString("00002101-0000-1000-8000-00805F9B34FB");
    private static final UUID OXI_CHAR_UUID = UUID.fromString("00002102-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;

    private GraphView healthGraph;
    private GraphView spo2Graph;

    private LineGraphSeries<DataPoint> heartRateSeries;
    private LineGraphSeries<DataPoint> oxygenSeries;

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    TextView heartRateView;
    TextView spo2View;
    private int heartRateIndex = 0;
    private int oxygenIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_patient_health);  // Make sure this is correct
        heartRateView = findViewById(R.id.heartRateTextView);
        spo2View =findViewById(R.id.OxiTextView);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);

        healthGraph = findViewById(R.id.healthGraph);
        spo2Graph = findViewById(R.id.spo2Graph);

        heartRateSeries = new LineGraphSeries<>();
        oxygenSeries = new LineGraphSeries<>();

        // Configure the graphs
        configureGraph(healthGraph);
        configureGraph(spo2Graph);

        checkBluetoothPermissions();  // Your Bluetooth connection code
    }

    // Method to check and request Bluetooth permissions
    private void checkBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
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
                        updateGraph(healthGraph, heartRateSeries, heartRate);
                    }
                });

            } else if(OXI_CHAR_UUID.equals(characteristic.getUuid())){
                int oxygenLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        spo2View.setText("O2: " + oxygenLevel);
                        updateGraph(spo2Graph, oxygenSeries, oxygenLevel);
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

    private void configureGraph(GraphView graph) {
        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(true);
        graph.getViewport().setScrollable(true);
        graph.getViewport().setScrollableY(true);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(60); // For example, 60 points on the X-axis, corresponding to 60 seconds

        graph.addSeries(new LineGraphSeries<DataPoint>());
    }

    // Method to update the graph with a new data point
    private void updateGraph(GraphView graph, LineGraphSeries<DataPoint> series, int value) {
        // Add a new data point with the current index (time) and value
        series.appendData(new DataPoint(heartRateIndex++, value), true, 60);
        graph.getViewport().setMaxX(heartRateIndex); // Update the max X value to reflect the latest time
    }
}
