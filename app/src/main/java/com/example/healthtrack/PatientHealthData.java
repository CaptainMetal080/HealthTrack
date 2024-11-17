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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;
import java.util.ArrayList;

public class PatientHealthData extends AppCompatActivity {
    private final String DEVICE_ADDRESS = "BC:B5:A2:5B:02:16";
    private static final int MAX_POINTS = 25;
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
    private static final int REQUEST_CALL_PHONE_PERMISSION = 2;
    TextView heartRateView;
    TextView spo2View;
    private int heartRateIndex;
    private int oxygenIndex;

    private float heartSum = 0;
    private ArrayList<Integer> heartRateReadings = new ArrayList<>();
    private final int heartSampleSize = 5;
    private final float THRESHOLD_MILD = 0.15f;
    private final float THRESHOLD_CRITICAL = 0.25f;

    private static final UUID MOBILE_SERVICE_UUID = UUID.fromString("0AC359B3-1A3B-4f8C-8378-E715F4F1B775");
    private static final UUID MOBILE_HEART_CHAR_UUID = UUID.fromString("4CE9B062-DE2D-4E36-BE57-5CE9D2F1418B");
    private static final UUID MOBILE_SPO2_CHAR_UUID = UUID.fromString("1009A25F-8934-4270-AAF6-E0D76AD669E5");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_patient_health);  // Make sure this is correct
        heartRateView = findViewById(R.id.heartRateTextView);
        spo2View = findViewById(R.id.OxiTextView);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        heartRateIndex = 0;
        oxygenIndex = 0;
        heartChart = findViewById(R.id.heartGraph);
        spo2Chart = findViewById(R.id.spo2Graph);
        device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);

        // Initialize the data sets
        heartRateDataSet = new LineDataSet(new ArrayList<>(), "Heart Rate");
        oxygenDataSet = new LineDataSet(new ArrayList<>(), "Oxygen Level");

        // Set line styles (optional)
        heartRateDataSet.setColor(getColor(R.color.healthy));
        heartRateDataSet.setCircleColor(getColor(R.color.healthy));
        oxygenDataSet.setColor(getColor(R.color.healthy));
        oxygenDataSet.setCircleColor(getColor(R.color.healthy));
        // Create LineData objects
        LineData heartRateData = new LineData(heartRateDataSet);
        LineData oxygenData = new LineData(oxygenDataSet);

        // Set data to charts
        heartChart.setData(heartRateData);
        spo2Chart.setData(oxygenData);

        // Configure the charts
        configureChart(heartChart,200);
        configureChart(spo2Chart,100);

        checkBluetoothPermissions();
        checkCallPhonePermission();
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

    private void checkCallPhonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted, request the permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CALL_PHONE_PERMISSION);
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

        if (requestCode == REQUEST_CALL_PHONE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, now you can make the call
                Log.d("Permissions", "CALL_PHONE permission granted.");
            } else {
                // Permission denied, inform the user
                Toast.makeText(this, "Phone call permission denied", Toast.LENGTH_SHORT).show();
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
                        if(heartRate >160){
                            Toast.makeText(PatientHealthData.this, "Critical: High Heart Rate!", Toast.LENGTH_SHORT).show();
                            Log.e("HealthWarning", "Critical BPM: " + heartRate);
                            spo2View.setTextColor(getColor(R.color.emergency));
                            heartRateDataSet.setColor(getColor(R.color.emergency));
                            heartRateDataSet.setCircleColor(getColor(R.color.emergency));
                            //Make Phone Call
                            callEmergency();
                        }
                        if(heartRate < 50){
                            Toast.makeText(PatientHealthData.this, "Critical: Slow Heart Rate!", Toast.LENGTH_SHORT).show();
                            Log.e("HealthWarning", "Critical BPM: " + heartRate);
                            spo2View.setTextColor(getColor(R.color.emergency));
                            heartRateDataSet.setColor(getColor(R.color.emergency));
                            heartRateDataSet.setCircleColor(getColor(R.color.emergency));
                            //Make Phone Call
                            callEmergency();
                        }else{
                            oxygenDataSet.setColor(getColor(R.color.healthy));
                            oxygenDataSet.setCircleColor(getColor(R.color.healthy));
                            spo2View.setTextColor(getColor(R.color.healthy));  // Reset to default
                        }

                        heartRateView.setText("Heart Rate: " + heartRate);
                        updateChart(heartChart,heartRateDataSet,heartRate,heartRateIndex);
                        heartRateIndex++;
                    }
                });
                sendHeartRateToMobileDevice(heartRate);

            } else if (OXI_CHAR_UUID.equals(characteristic.getUuid())) {
                int oxygenLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (oxygenLevel < 90) {
                            Toast.makeText(PatientHealthData.this, "Critical: Low SpO2 detected!", Toast.LENGTH_SHORT).show();
                            Log.e("HealthWarning", "Critical SpO2: " + oxygenLevel);
                            spo2View.setTextColor(getColor(R.color.emergency));
                            oxygenDataSet.setColor(getColor(R.color.emergency));
                            oxygenDataSet.setCircleColor(getColor(R.color.emergency));
                            //Make Phone Call
                            callEmergency();
                        } else if (oxygenLevel <=94){
                            Toast.makeText(PatientHealthData.this, "Warning: Mildly low SpO2 detected!", Toast.LENGTH_SHORT).show();
                            Log.w("HealthWarning", "Mildly low SpO2: " + oxygenLevel);
                            oxygenDataSet.setColor(getColor(R.color.mild));
                            oxygenDataSet.setCircleColor(getColor(R.color.mild));
                            spo2View.setTextColor(getColor(R.color.mild));
                        }else{
                            oxygenDataSet.setColor(getColor(R.color.healthy));
                            oxygenDataSet.setCircleColor(getColor(R.color.healthy));
                            spo2View.setTextColor(getColor(R.color.healthy));  // Reset to default
                        }
                        spo2View.setText("O2: " + oxygenLevel);
                        updateChart(spo2Chart,oxygenDataSet,oxygenLevel,oxygenIndex);
                        oxygenIndex++;
                    }
                });
                sendSpO2ToMobileDevice(oxygenLevel);
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

    private void callEmergency() {
        Intent phone_intent = new Intent(Intent.ACTION_CALL);
        phone_intent.setData(Uri.parse("tel:" + "enterNumberHEre"));
        startActivity(phone_intent);
    }

    // Configure the chart's appearance and properties
    private void configureChart(LineChart chart, float max) {
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.getDescription().setEnabled(false);
        chart.setPinchZoom(true);
        chart.getAxisRight().setEnabled(false);  // Disable right axis
        // Invert the X-axis to make data flow from right to left
        chart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setAxisMinimum(0f);  // Set X-axis minimum to 0
        chart.getXAxis().setGranularity(1f);  // Prevent duplicates on X-axis
        chart.getXAxis().setAxisMaximum(MAX_POINTS);  // Set max points on X-axis
        // Set the Y-axis properties
        chart.getAxisLeft().setAxisMinimum(0f); // Minimum Y-axis value
        chart.getAxisLeft().setAxisMaximum(max); // Maximum Y-axis value (adjust accordingly)
        chart.getAxisLeft().setGranularity(1f);  // Prevent duplicates on Y-axis
    }
    private void updateChart(LineChart chart, LineDataSet dataSet, int value, int index) {
        // Add a new data point to the dataset
        dataSet.addEntry(new Entry(index, value));

        // Keep only the last MAX_POINTS points in the chart (shift left after 50 points)
        if (dataSet.getEntryCount() > MAX_POINTS) {
            chart.getXAxis().setAxisMinimum(index - MAX_POINTS + 1); // Shift the axis left
            chart.getXAxis().setAxisMaximum(index + 1);  // Remove the oldest point
        }
        dataSet.setDrawCircles(true);
        dataSet.setCircleSize(3f);
        dataSet.setDrawValues(false);
        // Notify the dataset that the data has changed
        LineData data = chart.getData();
        if (data != null) {
            data.notifyDataChanged();
        } else {
            data = new LineData(dataSet);
            chart.setData(data);
        }

        // Notify the chart that the data has changed
        chart.notifyDataSetChanged();
        // Move the chart to the latest entry (this will keep the chart scrolled to the right)
        chart.moveViewToX(data.getEntryCount());
        // Refresh the chart
        chart.invalidate();
    }

    @SuppressLint("MissingPermission")
    private void sendHeartRateToMobileDevice(int heartRate) {
        if (bluetoothGatt != null) {
            BluetoothGattCharacteristic heartCharacteristic =
                    bluetoothGatt.getService(MOBILE_SERVICE_UUID).getCharacteristic(MOBILE_HEART_CHAR_UUID);

            if (heartCharacteristic != null) {
                heartCharacteristic.setValue(String.valueOf(heartRate).getBytes());
                boolean success = bluetoothGatt.writeCharacteristic(heartCharacteristic);
                if (success) {
                    Log.d("BluetoothGattCallback", "Heart Rate sent to mobile: " + heartRate);
                } else {
                    Log.e("BluetoothGattCallback", "Failed to send Heart Rate to mobile");
                }
            } else {
                Log.e("BluetoothGattCallback", "Heart Rate characteristic for mobile not found");
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void sendSpO2ToMobileDevice(int spo2) {
        if (bluetoothGatt != null) {
            BluetoothGattCharacteristic spo2Characteristic =
                    bluetoothGatt.getService(MOBILE_SERVICE_UUID).getCharacteristic(MOBILE_SPO2_CHAR_UUID);

            if (spo2Characteristic != null) {
                spo2Characteristic.setValue(String.valueOf(spo2).getBytes());
                boolean success = bluetoothGatt.writeCharacteristic(spo2Characteristic);
                if (success) {
                    Log.d("BluetoothGattCallback", "SpO2 sent to mobile: " + spo2);
                } else {
                    Log.e("BluetoothGattCallback", "Failed to send SpO2 to mobile");
                }
            } else {
                Log.e("BluetoothGattCallback", "SpO2 characteristic for mobile not found");
            }
        }
    }



}
