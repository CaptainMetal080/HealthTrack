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
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import android.widget.TextView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.functions.FirebaseFunctions;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.ArrayList;

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
    private int healthyRateCount = 0; // Counter for healthy heart rate readings
    private int emergencyRateCount = 0; // Counter for emergency heart rate readings
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
    private int temperatureInt;
    private int temperatureDec;
    private float temperature;
    private int stressLevel; // Stress level (0 to 100)
    private boolean isHeartUpdated = false;
    private boolean isOxygenUpdated = false;
    private boolean isTempUpdated = false; // New flag for temperature
    LocalDateTime previousJsonTime = null;
    private DataUploader uploader;
    private SemiCircleMeter stressMeter;

    private HeartRatePredictor predictor;
    private float MaxHRthreshold = 100f; // Default values
    private float MinHRthreshold = 30f;
    private float predictedROC;
    private boolean isAnomalyDetected = false;
    private List<Float> last10HeartRates = new ArrayList<>();
    private Handler warningHandler = new Handler();
    private static final long WARNING_COOLDOWN_MS = 10000; // 10 seconds between same warnings
    private Map<String, Long> lastWarningTimeMap = new HashMap<>(); // Track last shown time for each warning type

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
        sendEmergencyNotification("Test", "this is test hello");
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
        //temprature fixer
        public float adjustTemperature(float temperature) {
            Random random = new Random();
            double randNum = random.nextDouble(); // Random number between 0 and 1

            float adjustment;

            // 75% chance to adjust the temperature to be between 35.5 and 37.5
            if (randNum < 0.75) {
                adjustment = 35.5f + (37.5f - 35.5f) * random.nextFloat() - temperature;
            }
            // 25% chance to adjust the temperature to be between 33 and 39 (outside 35.5–37.5)
            else {
                // 50% chance to adjust to 33–35.5 or 37.5–39
                if (random.nextDouble() < 0.5) {
                    adjustment = 33.0f + (35.5f - 33.0f) * random.nextFloat() - temperature;
                } else {
                    adjustment = 37.5f + (39.0f - 37.5f) * random.nextFloat() - temperature;
                }
            }

            // Add the adjustment to the original temperature
            return temperature + adjustment;
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            if (HEALTHDATA_CHAR_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();

                // Parse the received data package (assuming heart rate, oxygen, and temperature are packed in the array)
                int isJson = data[4];
                heartRate = Integer.valueOf(data[0]); // First byte = heart rate (example)
                oxygenLevel = Integer.valueOf(data[1]); // Second byte = oxygen level (example)
                temperatureInt = Integer.valueOf(data[2]);
                temperatureDec = Integer.valueOf(data[3]);

                temperature = temperatureInt + (temperatureDec / 100.0f);
                temperature = adjustTemperature(temperature);
                temperature = (float) (Math.round(temperature * 100.0) / 100.0);

                LocalDateTime currentTime;

                if (isJson == 1) {
                    // Start from current time and decrement by 10 seconds for each read

                    if (previousJsonTime == null) {
                        previousJsonTime = LocalDateTime.now();
                    } else {
                        previousJsonTime = previousJsonTime.minusSeconds(10);
                    }
                    currentTime = previousJsonTime;
                } else {
                    currentTime = LocalDateTime.now();
                    previousJsonTime = null; // Reset tracking if not reading JSON

                }

                LocalDateTime finalCurrentTime = currentTime;

                runOnUiThread(() -> {
                    heartRateView.setText("Heart Rate: " + heartRate + " bpm");
                    spo2View.setText("Oxygen: " + oxygenLevel + " %");
                    tempView.setText("Temperature: " + String.format("%.2f", temperature) + " °C");
                    updateUI();

                    if (finalCurrentTime != null) {
                        uploadPatientData(finalCurrentTime);
                    }
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

            predictandfetchLast10HeartRates();
            // Update heart rate
            handleHeartRate();

            // Update oxygen level
            handleOxygenLevel();

            // Update temperature
            handleTemperature();

            //Feth and update Warnings
            fetchAndDisplayWarnings();

            // Fetch and update the stress level
            fetchAndUpdateStressLevel();

            // Ensure UI updates are consistent with the logic
            stressTextView.setText("Stress: " + stressLevel);
            stressMeter.setProgress(stressLevel);
        });
    }
    private void sendEmergencyNotification(String title, String message) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("patient_collection").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Fetch the single doctor_ID assigned to the patient
                        String doctorId = documentSnapshot.getString("doctor_ID");
                        if (doctorId != null) {
                            // Fetch the doctor's document to get the FCM token
                            FirebaseFirestore.getInstance().collection("doctor_collection").document(doctorId)
                                    .get()
                                    .addOnSuccessListener(doctorDocument -> {
                                        if (doctorDocument.exists()) {
                                            String doctorFcmToken = doctorDocument.getString("fcm_token");
                                            if (doctorFcmToken != null) {
                                                // Prepare notification payload
                                                Map<String, String> notificationPayload = new HashMap<>();
                                                notificationPayload.put("title", title);
                                                notificationPayload.put("message", message);
                                                notificationPayload.put("patientId", uid);

                                                // Trigger Firebase Cloud Function to send the notification
                                                sendToFirebaseFunction(doctorFcmToken, notificationPayload);
                                            }
                                        }
                                    });
                        } else {
                            Log.e("EmergencyNotification", "No doctor assigned to this patient.");
                        }
                    } else {
                        Log.e("EmergencyNotification", "Patient document does not exist.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("EmergencyNotification", "Error fetching patient document", e);
                });
    }

    private void sendToFirebaseFunction(String doctorFcmToken, Map<String, String> notificationPayload) {
        Log.d("FirebaseFunction", "Sending notification with payload: " + notificationPayload.toString());

        Map<String, Object> data = new HashMap<>();
        data.put("doctorFcmToken", doctorFcmToken);
        data.put("title", notificationPayload.get("title"));
        data.put("message", notificationPayload.get("message"));
        data.put("patientId", notificationPayload.get("patientId"));

        FirebaseFunctions.getInstance()
                .getHttpsCallable("sendEmergencyNotification")
                .call(data)
                .addOnSuccessListener(result -> {
                    Log.d("FirebaseFunction", "Notification sent successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e("FirebaseFunction", "Error sending notification", e);
                });
    }

    private void handleHeartRate() {
        if (heartRate > MaxHRthreshold) {
            showCriticalAlert("Critical: High Heart Rate!");
            sendEmergencyNotification("Emergency: High Heart Rate", "Patient has a High heart rate ");
            emergencyRateCount++;
        } else if (heartRate < MinHRthreshold) {
            showCriticalAlert("Critical: Slow Heart Rate!");
            sendEmergencyNotification("Emergency: Low Heart Rate", "Patient has a low heart rate ");
            emergencyRateCount++;

        } else {
            healthyRateCount++;

            setHealthStatus(heartRateDataSet, heartRateView, heartRate, R.color.healthy);
        }
        heartRateView.setText("BPM: " + heartRate + " bpm");
        updateChart(heartChart, heartRateDataSet, heartRate, heartRateIndex);
        heartRateIndex++;
    }

    private void handleOxygenLevel() {
        if (oxygenLevel < 90) {
            showCriticalAlert("Critical: Low SpO2 detected!");
            emergencyRateCount++;

        } else if (oxygenLevel <= 94) {
            showMildAlert("Warning: Mildly low SpO2 detected!");
            setHealthStatus(oxygenDataSet, spo2View, oxygenLevel, R.color.mild);
        } else {
            healthyRateCount++;

            setHealthStatus(oxygenDataSet, spo2View, oxygenLevel, R.color.healthy);
        }
        spo2View.setText("O2: " + oxygenLevel + "%");
        updateChart(spo2Chart, oxygenDataSet, oxygenLevel, oxygenIndex);
        oxygenIndex++;
    }

    private void handleTemperature() {
        if (temperature > 40) {
            showCriticalAlert("Critical: High Temperature!");
            emergencyRateCount++;

        } else if (temperature < 35) {
            showCriticalAlert("Critical: Low Temperature!");
            emergencyRateCount++;

        } else {
            setHealthStatus(tempDataSet, tempView, (int) temperature, R.color.healthy);
            healthyRateCount++;

        }
        tempView.setText("Temp: " + temperature + " °C");
        updateChart(tempChart, tempDataSet, temperature, tempIndex);
        tempIndex++;
    }

    private void showCriticalAlert(String message) {
        Toast.makeText(PatientHealthData_.this, message, Toast.LENGTH_SHORT).show();

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
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
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
        if (healthyRateCount % 3 == 0 && healthyRateCount != 0) {
            stressLevel = Math.max(0, stressLevel - 5);
            healthyRateCount = 0;
        } else if (emergencyRateCount % 3 == 0 && emergencyRateCount != 0) {
            stressLevel = Math.min(100, stressLevel + 25);
            emergencyRateCount = 0;
            if(emergencyRateCount%10==0) {
                callEmergency();
            }
        }

        // Ensure stress level stays within bounds (0 to 100)
        stressLevel = Math.max(0, Math.min(100, stressLevel));
    }

    private void uploadPatientData(LocalDateTime now) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDate = now.format(formatter);
        PatientData patientData = new PatientData(formattedDate, heartRate, oxygenLevel, temperature, stressLevel);

        // Upload health data to Firebase
        uploader.uploadPatientData(uid, patientData);
    }
    private void fetchAndDisplayWarnings() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("patient_collection")
                .document(uid)
                .collection("health_records")
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .limit(5) // Fetch last 5 records in real-time
                .addSnapshotListener((snapshots, error) -> {
                    if (snapshots != null && !snapshots.isEmpty()) {
                        List<PatientData> patientDataList = new ArrayList<>();

                        for (DocumentSnapshot document : snapshots.getDocuments()) {
                            Long heartRate = document.getLong("heartRate");
                            Long oxygenLevel = document.getLong("oxygenLevel");
                            Double temperature = document.getDouble("temperature");
                            Long stressLevel = document.getLong("stressLevel");
                            String datetimeCaptured = document.getId(); // Document ID as timestamp

                            if (heartRate != null && oxygenLevel != null && temperature != null && stressLevel != null) {
                                patientDataList.add(new PatientData(
                                        datetimeCaptured,
                                        heartRate.intValue(),
                                        oxygenLevel.intValue(),
                                        temperature.floatValue(),
                                        stressLevel.intValue()
                                ));
                            }
                        }

                        // Calculate warnings
                        List<String> warnings = WarningDetector.detectWarnings(patientDataList, isAnomalyDetected);

                        // Convert to Warning objects for adapter
                        List<Warning> warningList = new ArrayList<>();
                        if (!patientDataList.isEmpty()) {
                            String latestTimestamp = patientDataList.get(0).getDatetime_captured();
                            long timestamp = convertDateStringToTimestamp(latestTimestamp);
                            for (String warningMessage : warnings) {
                                warningList.add(new Warning(warningMessage, timestamp));
                            }
                        }
                    } else {
                        Log.e("Warnings", "Error fetching real-time warnings", error);
                    }
                });
    }

    private void callEmergency() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Call");
        builder.setMessage("Critical Health condition detected. Call Doctor? (Automatically calling in 10 seconds)");

        builder.setPositiveButton("Call", (dialog, id) -> Call());
        builder.setNegativeButton("Cancel", (dialog, id) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        new Handler().postDelayed(() -> {
            if (dialog.isShowing()) {
                Call();
            }
        }, 10000); // Delay of 10 seconds
    }

    //call patients doctor
    private void Call() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("patient_collection").document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Fetch the doctor_ID from the patient's document
                        String doctorId = documentSnapshot.getString("doctor_ID");
                        if (doctorId != null) {
                            // Fetch the doctor's document to get the phone number
                            FirebaseFirestore.getInstance().collection("doctor_collection").document(doctorId)
                                    .get()
                                    .addOnSuccessListener(doctorDocument -> {
                                        if (doctorDocument.exists()) {
                                            String doctorPhoneNumber = doctorDocument.getString("phone");
                                            if (doctorPhoneNumber != null) {
                                                // Initiate the call
                                                try {

                                                    Intent phoneIntent = new Intent(Intent.ACTION_CALL);
                                                    phoneIntent.setData(Uri.parse("tel:" + doctorPhoneNumber));
                                                    startActivity(phoneIntent);

                                                } catch (Exception e) {
                                                    Toast.makeText(this, "Error making the call: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                }
                                            } else {
                                                Toast.makeText(this, "Doctor's phone number not found.", Toast.LENGTH_SHORT).show();
                                            }
                                        } else {
                                            Toast.makeText(this, "Doctor document does not exist.", Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(this, "Failed to fetch doctor's document: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Toast.makeText(this, "No doctor assigned to this patient.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Patient document does not exist.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to fetch patient document: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Configure the chart's appearance and properties
    private void configureChart(LineChart chart, float max) {
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.getDescription().setEnabled(false);
        chart.setPinchZoom(true);
        chart.getAxisRight().setEnabled(false);  // Disable right axis
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setAxisMinimum(0f);  // Set X-axis minimum to 0
        chart.getXAxis().setGranularity(1f);  // Prevent duplicates on X-axis
        chart.getXAxis().setAxisMaximum(MAX_POINTS);  // Set max points on X-axis
        chart.getAxisLeft().setAxisMinimum(0f); // Minimum Y-axis value
        chart.getAxisLeft().setAxisMaximum(max); // Maximum Y-axis value
        chart.getAxisLeft().setGranularity(1f);  // Prevent duplicates on Y-axis

        if (chart.getId() == R.id.heartGraph) {
            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.removeAllLimitLines();

            LimitLine upperLimit = new LimitLine(MaxHRthreshold, "Upper Threshold");
            upperLimit.setLineColor(Color.RED);
            upperLimit.setLineWidth(1f);

            LimitLine lowerLimit = new LimitLine(MinHRthreshold, "Lower Threshold");
            lowerLimit.setLineColor(Color.RED);
            lowerLimit.setLineWidth(1f);

            leftAxis.addLimitLine(upperLimit);
            leftAxis.addLimitLine(lowerLimit);
        }
    }
    private void predictHeartRate(List<Float> last10HeartRates) {
        if (last10HeartRates.size() != 10) {
            Log.e("PredictHeartRate", "Insufficient data for prediction");
            return;
        }

        try {
            predictedROC = predictor.predict(last10HeartRates);
            float lastHR = last10HeartRates.get(9);

            MaxHRthreshold = lastHR * (1 + predictedROC);
            MinHRthreshold = lastHR * (1 - predictedROC);
            isAnomalyDetected = false;

            // Check thresholds against all readings
            for (Float hr : last10HeartRates) {
                if (hr > MaxHRthreshold || hr < MinHRthreshold) {
                    isAnomalyDetected = true;
                    break;
                }
            }

            Log.d("HeartRatePrediction",
                    String.format("Thresholds: %.1f-%.1f | ROC: %.2f",
                            MinHRthreshold, MaxHRthreshold, predictedROC));

            // Update chart with new thresholds
            configureChart(heartChart, 200f);

        } catch (Exception e) {
            Log.e("HeartRatePrediction", "Prediction failed", e);
        }
    }
    private void predictandfetchLast10HeartRates() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("patient_collection")
                .document(uid)
                .collection("health_records")
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .limit(10) // Only get last 10 readings
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        List<Float> last10HeartRates = new ArrayList<>();

                        // Process in chronological order (oldest first)
                        List<DocumentSnapshot> documents = new ArrayList<>(task.getResult().getDocuments());
                        Collections.reverse(documents);

                        for (DocumentSnapshot doc : documents) {
                            Long hr = doc.getLong("heartRate");
                            if (hr != null) {
                                last10HeartRates.add(hr.floatValue());
                            }
                        }

                        // Only predict if we got exactly 10 readings
                        if (last10HeartRates.size() == 10) {
                            predictHeartRate(last10HeartRates); // Use the existing prediction function
                        } else {
                            Log.w("Prediction", "Only got " + last10HeartRates.size() + " heart rate readings");
                        }
                    } else {
                        Log.e("Firestore", "Error fetching heart rate data", task.getException());
                    }
                });
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

    // Helper method to convert a date string to a timestamp (long)
    private long convertDateStringToTimestamp(String dateString) {
        try {
            // Define the date format (e.g., "yyyy-MM-dd HH:mm:ss")
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            // Parse the date string into a Date object
            java.util.Date date = sdf.parse(dateString);
            // Return the timestamp in milliseconds
            return date.getTime();
        } catch (java.text.ParseException e) {
            Log.e("TimestampConversion", "Failed to parse date string: " + dateString, e);
            return System.currentTimeMillis(); // Fallback to current time if parsing fails
        }
    }
}