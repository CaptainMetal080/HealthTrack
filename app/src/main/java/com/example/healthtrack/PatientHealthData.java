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
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Button;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    private int oxygenLevel;
    private int heartRate;
    private boolean isHeartUpdated = false;
    private boolean isOxygenUpdated = false;
    private DataUploader uploader;

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
                //Log.d("Permissions", "CALL_PHONE permission granted.");
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
                //Log.d("BluetoothGattCallback", "Connected to GATT server.");

                // Check permission before discovering services
                if (ContextCompat.checkSelfPermission(PatientHealthData.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.discoverServices(); // Start service discovery
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //Log.d("BluetoothGattCallback", "Disconnected from GATT server.");
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //Log.d("BluetoothGattCallback", "Services discovered.");

                // Get heart rate and oxygen characteristics
                BluetoothGattCharacteristic heartRateCharacteristic = gatt.getService(SERVICE_UUID).getCharacteristic(HEARTRATE_CHAR_UUID);

                gatt.setCharacteristicNotification(heartRateCharacteristic, true);

                // Write the descriptor to enable notifications
                BluetoothGattDescriptor heartRateDescriptor = heartRateCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                if (heartRateDescriptor != null) {
                    heartRateDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    //Log.d("BluetoothGattCallback", "Heart Notif");
                    gatt.writeDescriptor(heartRateDescriptor); // Enable heart rate notifications
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (HEARTRATE_CHAR_UUID.equals(characteristic.getUuid())) {
                heartRate = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

                // Update the UI using runOnUiThread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(heartRate >160){
                            Toast.makeText(PatientHealthData.this, "Critical: High Heart Rate!", Toast.LENGTH_SHORT).show();
                            Log.e("HealthWarning", "Critical BPM: " + heartRate);
                            heartRateView.setTextColor(getColor(R.color.emergency));
                            heartRateDataSet.setColor(getColor(R.color.emergency));
                            heartRateDataSet.setCircleColor(getColor(R.color.emergency));
                            //Make Phone Call
                            callEmergency();
                        }
                        if(heartRate < 50){
                            Toast.makeText(PatientHealthData.this, "Critical: Slow Heart Rate!", Toast.LENGTH_SHORT).show();
                            Log.e("HealthWarning", "Critical BPM: " + heartRate);
                            heartRateView.setTextColor(getColor(R.color.emergency));
                            heartRateDataSet.setColor(getColor(R.color.emergency));
                            heartRateDataSet.setCircleColor(getColor(R.color.emergency));
                            //Make Phone Call
                            callEmergency();
                        }else{
                            heartRateDataSet.setColor(getColor(R.color.healthy));
                            heartRateDataSet.setCircleColor(getColor(R.color.healthy));
                            heartRateView.setTextColor(getColor(R.color.healthy));  // Reset to default
                        }

                        heartRateView.setText("BPM: %.1f bpm" + heartRate);
                        updateChart(heartChart,heartRateDataSet,heartRate,heartRateIndex);
                        heartRateIndex++;
                        isHeartUpdated=true;
                    }

                });

            } else if (OXI_CHAR_UUID.equals(characteristic.getUuid())) {
                oxygenLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

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
                        spo2View.setText("O2: %.1f%%" + oxygenLevel);
                        updateChart(spo2Chart,oxygenDataSet,oxygenLevel,oxygenIndex);
                        oxygenIndex++;
                        isOxygenUpdated=true;

                        LocalDateTime now = LocalDateTime.now();

                        // Format as 'YYYY-MM-DD HH:MM:SS'
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        String formattedDate = now.format(formatter);


                        Intent intent = getIntent();
                        String pId=intent.getStringExtra("id");

                        PatientData patientData = new PatientData(pId, formattedDate, heartRate, oxygenLevel);
                        // Insert this data into the database
                        uploader.uploadPatientData(patientData);
                       // if (dbHelper.getAllPatientData().getCount() % 10 == 0) {
                       //     DataUploader uploader = new DataUploader(PatientHealthData.this);
                       //     uploader.uploadPatientDataBatch();
                       // }

                        isHeartUpdated = false;
                        isOxygenUpdated = false;
                    }
                });
            }

            /*
            if(isHeartUpdated && isOxygenUpdated){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.e("Database", "It Works :)");
                        long timestamp = System.currentTimeMillis();
                        Intent intent = getIntent();
                        int pId=Integer.parseInt(intent.getStringExtra("id"));

                        PatientData patientData = new PatientData(pId, timestamp, heartRate, oxygenLevel);
                        // Insert this data into the database
                        dbHelper.addPatientData(patientData);

                        if (dbHelper.getAllPatientData().getCount() % 10 == 0) {
                            DataUploader uploader = new DataUploader(PatientHealthData.this);
                            uploader.uploadPatientDataBatch();
                        }

                        isHeartUpdated = false;
                        isOxygenUpdated = false;
                    }
                });
            }*/
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //Log.d("DescriptorWrite", "Descriptor write successful for: " + descriptor.getUuid().toString());
                BluetoothGattCharacteristic oxygenCharacteristic = gatt.getService(SERVICE_UUID).getCharacteristic(OXI_CHAR_UUID);
                gatt.setCharacteristicNotification(oxygenCharacteristic, true);
                BluetoothGattDescriptor oxygenDescriptor = oxygenCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                if (oxygenDescriptor != null) {
                    oxygenDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    //Log.d("BluetoothGattCallback", "O2 Notif");
                    gatt.writeDescriptor(oxygenDescriptor); // Enable SpO2 notifications
                }
            } else {
                Log.e("DescriptorWrite", "Descriptor write failed for: " + descriptor.getUuid().toString());
            }
        }
    };

    private void callEmergency() {
        // Show confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Call");
        builder.setMessage("Critical Health condition detected call emergency?");

        // Positive button to call emergency
        builder.setPositiveButton("Call", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Call();  // Proceed with the call
            }
        });

        // Negative button to cancel
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();  // Dismiss the dialog without calling
            }
        });

        // Create and show the dialog
        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                // Get reference to the positive and negative buttons
                Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                Button negativeButton = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);

                // Set the colors of the buttons (use your custom color values)
                positiveButton.setTextColor(ContextCompat.getColor(PatientHealthData.this, R.color.healthy));  // Change to your desired color
                negativeButton.setTextColor(ContextCompat.getColor(PatientHealthData.this, R.color.heartred));  // Change to your desired color
            }
        });
        dialog.show();

        // Create a handler to automatically proceed with the call after 5 seconds if the user doesn't interact
        Handler handler = new Handler();
        Runnable callRunnable = new Runnable() {
            @Override
            public void run() {
                if (dialog.isShowing()) {
                    // If the dialog is still showing after 5 seconds, proceed with the call
                    Call();
                }
            }
        };
        handler.postDelayed(callRunnable, 5000);  // Delay of 5000ms (5 seconds)
    }

    private void Call() {
        try {
            // Make the call
            Intent phone_intent = new Intent(Intent.ACTION_CALL);
            phone_intent.setData(Uri.parse("tel:" + "enterNumberHere"));  // Replace with actual number
            startActivity(phone_intent);
        } catch (Exception e) {
            // Handle exception if the phone intent fails
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
}
