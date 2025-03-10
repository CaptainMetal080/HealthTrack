package com.example.healthtrack;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;



public class PatientHealthData_ extends AppCompatActivity {
    private final String DEVICE_ADDRESS = "BC:B5:A2:5B:02:16";
    private static final int MAX_POINTS = 25;
    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID HEALTHDATA_CHAR_UUID = UUID.fromString("00002101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;

    private LineChart heartChart;
    private LineChart spo2Chart;
    private LineChart tempChart; // New chart for temperature

    private LineDataSet heartRateDataSet;
    private LineDataSet oxygenDataSet;
    private LineDataSet tempDataSet; // New data set for temperature
    private int healthyHeartRateCount = 0; // Counter for healthy heart rate readings
    private int emergencyHeartRateCount = 0; // Counter for emergency heart rate readings
    private int healthyTempCount = 0; // Counter for healthy temperature readings
    private int emergencyTempCount = 0; // Counter for emergency temperature readings

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final int REQUEST_CALL_PHONE_PERMISSION = 2;
    TextView heartRateView;
    TextView spo2View;
    TextView tempView; // New TextView for temperature
    TextView stressTextView; // Stress text view

    private int heartRateIndex;
    private int oxygenIndex;
    private int tempIndex; // New index for temperature

    private int oxygenLevel;
    private int heartRate;
    private float temperature; // New variable for temperature
    private int stressLevel; // Stress level (0 to 100)
    private boolean isHeartUpdated = false;
    private boolean isOxygenUpdated = false;
    private boolean isTempUpdated = false; // New flag for temperature
    private DataUploader uploader;
    private SemiCircleMeter stressMeter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_patient_health);  // Make sure this is correct
        heartRateView = findViewById(R.id.heartRateTextView);
        spo2View = findViewById(R.id.OxiTextView);
        tempView = findViewById(R.id.TempTextView); // Initialize temperature TextView
        stressTextView = findViewById(R.id.stressTextView); // Stress text view
        stressMeter = findViewById(R.id.stressMeter); // Stress meter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        heartRateIndex = 0;
        oxygenIndex = 0;
        tempIndex = 0; // Initialize temperature index
        heartChart = findViewById(R.id.heartGraph);
        spo2Chart = findViewById(R.id.spo2Graph);
        tempChart = findViewById(R.id.tempGraph); // Initialize temperature chart
        device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);

        // Initialize the data sets
        heartRateDataSet = new LineDataSet(new ArrayList<>(), "Heart Rate");
        oxygenDataSet = new LineDataSet(new ArrayList<>(), "Oxygen Level");
        tempDataSet = new LineDataSet(new ArrayList<>(), "Temperature"); // Initialize temperature data set

        // Set line styles (optional)
        heartRateDataSet.setColor(getColor(R.color.healthy));
        heartRateDataSet.setCircleColor(getColor(R.color.healthy));
        oxygenDataSet.setColor(getColor(R.color.healthy));
        oxygenDataSet.setCircleColor(getColor(R.color.healthy));
        tempDataSet.setColor(getColor(R.color.healthy)); // Set temperature line color
        tempDataSet.setCircleColor(getColor(R.color.healthy));

        // Create LineData objects
        LineData heartRateData = new LineData(heartRateDataSet);
        LineData oxygenData = new LineData(oxygenDataSet);
        LineData tempData = new LineData(tempDataSet); // Create temperature data

        // Set data to charts
        heartChart.setData(heartRateData);
        spo2Chart.setData(oxygenData);
        tempChart.setData(tempData); // Set temperature data

        // Configure the charts
        configureChart(heartChart, 200);
        configureChart(spo2Chart, 100);
        configureChart(tempChart, 50); // Configure temperature chart

        checkBluetoothPermissions();
        checkCallPhonePermission();

        // Initialize DataUploader
        uploader = new DataUploader(this);

        heartChart.setOnClickListener(v -> {
            Intent intent = new Intent(this, GraphDetailActivity.class);
            intent.putExtra("patientId", FirebaseAuth.getInstance().getCurrentUser().getUid());
            intent.putExtra("graphType", "heartRate");
            startActivity(intent);
        });

        spo2Chart.setOnClickListener(v -> {
            Intent intent = new Intent(this, GraphDetailActivity.class);
            intent.putExtra("patientId", FirebaseAuth.getInstance().getCurrentUser().getUid());
            intent.putExtra("graphType", "oxygenLevel");
            startActivity(intent);
        });

        tempChart.setOnClickListener(v -> {
            Intent intent = new Intent(this, GraphDetailActivity.class);
            intent.putExtra("patientId", FirebaseAuth.getInstance().getCurrentUser().getUid());
            intent.putExtra("graphType", "temperature");
            startActivity(intent);
        });

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
                // Check permission before discovering services
                if (ContextCompat.checkSelfPermission(PatientHealthData_.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices(); // Start service discovery
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Handle disconnection
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Get the characteristic that contains the combined data (heart rate, oxygen, and temperature)
                BluetoothGattCharacteristic healthDataCharacteristic = gatt.getService(SERVICE_UUID).getCharacteristic(HEALTHDATA_CHAR_UUID);

                // Enable notifications for this single characteristic (which contains all the data)
                gatt.setCharacteristicNotification(healthDataCharacteristic, true);
                BluetoothGattDescriptor descriptor = healthDataCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }

            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (HEALTHDATA_CHAR_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();

                // Parse the received data package (assuming heart rate, oxygen, and temperature are packed in the array)
                heartRate = Integer.valueOf(data[0]); // First byte = heart rate (example)
                oxygenLevel = Integer.valueOf(data[1]); // Second byte = oxygen level (example)
                temperature = Integer.valueOf(data[2]);

                // Update UI and charts
                runOnUiThread(() -> {
                    heartRateView.setText("Heart Rate: " + heartRate + " bpm");
                    spo2View.setText("Oxygen: " + oxygenLevel + " %");
                    tempView.setText("Temperature: " + temperature + " °C");
                    updateUI();
                });
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Handle descriptor write success
            } else {
                Log.e("DescriptorWrite", "Descriptor write failed for: " + descriptor.getUuid().toString());
            }
        }
    };

    private void updateUI() {
        runOnUiThread(() -> {
            // Update heart rate
            handleHeartRate();

            // Update oxygen level
            handleOxygenLevel();

            // Update temperature
            handleTemperature();

            // Fetch and update the stress level
            fetchAndUpdateStressLevel();

            // Ensure UI updates are consistent with the logic
            stressTextView.setText("Stress: " + stressLevel);
            stressMeter.setProgress(stressLevel);

            // Upload data to Firestore
            uploadPatientData();
        });
    }

    private void handleHeartRate() {
        if (heartRate > 160) {
            showCriticalAlert("Critical: High Heart Rate!");
            emergencyHeartRateCount++;
            healthyHeartRateCount = 0;
            callEmergency();
        } else if (heartRate < 50) {
            showCriticalAlert("Critical: Slow Heart Rate!");
            emergencyHeartRateCount++;
            healthyHeartRateCount = 0;
            callEmergency();
        } else {
            healthyHeartRateCount++;
            emergencyHeartRateCount = 0;
            setHealthStatus(heartRateDataSet, heartRateView, heartRate, R.color.healthy);
        }
        heartRateView.setText("BPM: " + heartRate + " bpm");
        updateChart(heartChart, heartRateDataSet, heartRate, heartRateIndex);
        heartRateIndex++;
    }

    private void handleOxygenLevel() {
        if (oxygenLevel < 90) {
            showCriticalAlert("Critical: Low SpO2 detected!");
            callEmergency();
        } else if (oxygenLevel <= 94) {
            showMildAlert("Warning: Mildly low SpO2 detected!");
            setHealthStatus(oxygenDataSet, spo2View, oxygenLevel, R.color.mild);
        } else {
            setHealthStatus(oxygenDataSet, spo2View, oxygenLevel, R.color.healthy);
        }
        spo2View.setText("O2: " + oxygenLevel + "%");
        updateChart(spo2Chart, oxygenDataSet, oxygenLevel, oxygenIndex);
        oxygenIndex++;
    }

    private void handleTemperature() {
        if (temperature > 40) {
            showCriticalAlert("Critical: High Temperature!");
            emergencyTempCount++;
            healthyTempCount = 0;
        } else if (temperature < 35) {
            showCriticalAlert("Critical: Low Temperature!");
            emergencyTempCount++;
            healthyTempCount = 0;
        } else {
            setHealthStatus(tempDataSet, tempView, (int) temperature, R.color.healthy);
        }
        tempView.setText("Temp: " + temperature + " °C");
        updateChart(tempChart, tempDataSet, temperature, tempIndex);
        tempIndex++;
    }

    private void showCriticalAlert(String message) {
        Toast.makeText(PatientHealthData_.this, message, Toast.LENGTH_SHORT).show();
        callEmergency();
    }

    private void showMildAlert(String message) {
        Toast.makeText(PatientHealthData_.this, message, Toast.LENGTH_SHORT).show();
    }

    private void setHealthStatus(LineDataSet dataSet, TextView view, int value, int color) {
        dataSet.setColor(getColor(color));
        dataSet.setCircleColor(getColor(color));
        view.setTextColor(getColor(color));
    }

    private void fetchAndUpdateStressLevel() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection("patient_collection").document(uid)
                .collection("health_records")
                .orderBy("datetime_captured", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot latestRecord = task.getResult().getDocuments().get(0);
                        Long fetchedStressLevel = latestRecord.getLong("stressLevel");

                        if (fetchedStressLevel != null) {
                            stressLevel = fetchedStressLevel.intValue();
                        }
                    } else {
                        Log.e("Firestore", "Failed to fetch latest stress level", task.getException());
                    }

                    applyStressLevelChanges();
                });
    }

    private void applyStressLevelChanges() {
        if (healthyHeartRateCount >= 2) {
            stressLevel = Math.max(0, stressLevel - 4);
            healthyHeartRateCount = 0;
        } else if (emergencyHeartRateCount >= 2) {
            stressLevel = Math.min(100, stressLevel + 7);
            emergencyHeartRateCount = 0;
        }

        // Ensure stress level stays within bounds (0 to 100)
        stressLevel = Math.max(0, Math.min(100, stressLevel));
    }

    private void uploadPatientData() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDate = now.format(formatter);

        PatientData patientData = new PatientData(formattedDate, heartRate, oxygenLevel, temperature, stressLevel);
        uploader.uploadPatientData(uid, patientData);
    }



    private void callEmergency() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Emergency Call");
//        builder.setMessage("Critical Health condition detected. Call emergency?");
//
//        builder.setPositiveButton("Call", (dialog, id) -> Call());
//        builder.setNegativeButton("Cancel", (dialog, id) -> dialog.dismiss());
//
//        AlertDialog dialog = builder.create();
//        dialog.show();
//
//        new Handler().postDelayed(() -> {
//            if (dialog.isShowing()) {
//                Call();
//            }
//        }, 5000); // Delay of 5 seconds
    }

    private void Call() {
        try {
            Intent phoneIntent = new Intent(Intent.ACTION_CALL);
            phoneIntent.setData(Uri.parse("tel:1234123412"));  // Replace with actual number
            startActivity(phoneIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Error making the call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Configure the chart's appearance and properties
    private void configureChart(LineChart chart, float max) {
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.getDescription().setEnabled(false);
        chart.setPinchZoom(true);
        chart.getAxisRight().setEnabled(false);  // Disable right axis
        chart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setAxisMinimum(0f);  // Set X-axis minimum to 0
        chart.getXAxis().setGranularity(1f);  // Prevent duplicates on X-axis
        chart.getXAxis().setAxisMaximum(MAX_POINTS);  // Set max points on X-axis
        chart.getAxisLeft().setAxisMinimum(0f); // Minimum Y-axis value
        chart.getAxisLeft().setAxisMaximum(max); // Maximum Y-axis value
        chart.getAxisLeft().setGranularity(1f);  // Prevent duplicates on Y-axis
    }

    private void updateChart(LineChart chart, LineDataSet dataSet, float value, int index) {
        dataSet.addEntry(new Entry(index, value));

        if (dataSet.getEntryCount() > MAX_POINTS) {
            chart.getXAxis().setAxisMinimum(index - MAX_POINTS + 1); // Shift the axis left
            chart.getXAxis().setAxisMaximum(index + 1);  // Remove the oldest point
        }

        LineData data = chart.getData();
        if (data != null) {
            data.notifyDataChanged();
        } else {
            data = new LineData(dataSet);
            chart.setData(data);
        }

        chart.notifyDataSetChanged();
        chart.moveViewToX(data.getEntryCount());
        chart.invalidate();
    }
}