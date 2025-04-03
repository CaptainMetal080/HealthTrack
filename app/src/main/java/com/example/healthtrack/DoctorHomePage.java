package com.example.healthtrack;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DoctorHomePage extends AppCompatActivity {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private LinearLayout patientListLayout;
    private String doctorId;
    private TextView drTitle;
    private Button refreshButton;
    private boolean isAnomalyDetected = false;
    private HeartRatePredictor predictor;
    float MaxHRthreshold;
    float MinHRthreshold;
    private AnomalyTracker heartRateAnomalyTracker = new AnomalyTracker();
    private int currentDataIndex = 0; // To track the position of new data
    private static final int MAX_POINTS = 25; // Consistent with PatientHealthData_nosensor

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_home_screen);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
            }
        }

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        patientListLayout = findViewById(R.id.patientListLayout);
        drTitle = findViewById(R.id.drTitle);
        refreshButton = findViewById(R.id.refreshButton);

        doctorId = mAuth.getCurrentUser().getUid();

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult();
                        Log.d("FCM Token", "FCM Token: " + token);

                        // Save the token to Firestore or Realtime Database
                        FirebaseFirestore.getInstance().collection("doctor_collection")
                                .document(doctorId) // Use the user ID to store the token
                                .update("fcm_token", token);
                    } else {
                        Log.w("FCM Token", "Fetching FCM token failed", task.getException());
                    }
                });

        fetchDoctorLastName();
        fetchAssignedPatients();

        refreshButton.setOnClickListener(v -> refreshActivity());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, you can now post notifications
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                // Permission denied, inform the user
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void refreshActivity() {
        // Clear the existing patient views
        patientListLayout.removeAllViews();
        // Fetch the latest data
        fetchDoctorLastName();
        fetchAssignedPatients();
    }

    private void fetchDoctorLastName() {
        db.collection("doctor_collection").document(doctorId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String lastName = document.getString("last_name");
                        drTitle.setText("Welcome Dr. " + lastName);
                    }
                });
    }

    private void fetchAssignedPatients() {
        db.collection("doctor_collection").document(doctorId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        List<String> patientIds = (List<String>) document.get("patient_list");
                        if (patientIds != null) {
                            for (String patientId : patientIds) {
                                fetchPatientData(patientId);
                            }
                        }
                    }
                });
    }

    private void fetchPatientData(String patientId) {
        db.collection("patient_collection").document(patientId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String firstName = document.getString("first_name");
                        String lastName = document.getString("last_name");
                        addPatientView(patientId, firstName + " " + lastName);
                    }
                });
    }

    private void addPatientView(String patientId, String patientName) {
        // Inflate the patient item layout
        View patientView = LayoutInflater.from(this).inflate(R.layout.patient_item, null);

        // Find views
        TextView nameView = patientView.findViewById(R.id.patientName);
        LineChart heartChart = patientView.findViewById(R.id.heartChart);
        TextView heartText = patientView.findViewById(R.id.heartRateTextView);
        LineChart spo2Chart = patientView.findViewById(R.id.spo2Chart);
        TextView spo2Text = patientView.findViewById(R.id.OxiTextView);
        LineChart tempChart = patientView.findViewById(R.id.tempChart);
        TextView tempText = patientView.findViewById(R.id.tempTextView);
        SemiCircleMeter stressMeter = patientView.findViewById(R.id.stressMeter); // Stress meter
        TextView stressText = patientView.findViewById(R.id.stressTextView);

        // Set patient name
        nameView.setText(patientName);

        // Configure charts
        configureChart(heartChart, 200f);
        configureChart(spo2Chart, 100f);
        configureChart(tempChart, 50f); // Configure temperature chart

        predictor = new HeartRatePredictor(this);
        // Fetch and plot patient data
        fetchPatientGraphs(patientId, heartChart, spo2Chart, tempChart, heartText, spo2Text, tempText, stressMeter, stressText);

        // Add click listener to the patient view
        patientView.setOnClickListener(v -> {
            // Log the patientId being passed
            Log.d("PatientClick", "Patient ID: " + patientId);

            // Create an intent to open PatientDetailActivity
            Intent intent = new Intent(this, PatientDetailActivity.class);
            intent.putExtra("patientId", patientId); // Pass the patientId to the next activity
            startActivity(intent);
        });
        // Add the patient view to the layout
        patientListLayout.addView(patientView);
    }

    private void fetchPatientGraphs(String patientId, LineChart heartChart, LineChart spo2Chart, LineChart tempChart,
                                    TextView heartText, TextView spo2Text, TextView tempText,
                                    SemiCircleMeter stressMeter, TextView stressText) {
        db.collection("patient_collection").document(patientId)
                .collection("health_records")
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .limit(MAX_POINTS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e("Firestore", "Error fetching data", error);
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        Log.w("Firestore", "No data available for patient: " + patientId);
                        return;
                    }

                    // Process data in background thread
                    new Thread(() -> {
                        List<Entry> heartEntries = new ArrayList<>();
                        List<Entry> spo2Entries = new ArrayList<>();
                        List<Entry> tempEntries = new ArrayList<>();
                        List<Entry> stressEntries = new ArrayList<>();
                        List<Float> predictionEntries = new ArrayList<>();

                        // Get documents in chronological order (oldest first)
                        List<DocumentSnapshot> documents = new ArrayList<>(snapshots.getDocuments());
                        Collections.reverse(documents);
                        currentDataIndex = 0; // Reset counter on new data
                        for (DocumentSnapshot document : documents) {

                            Long heartRate = document.getLong("heartRate");
                            Long oxygenLevel = document.getLong("oxygenLevel");
                            Double temperature = document.getDouble("temperature");
                            Long stressLevel = document.getLong("stressLevel");

                            if (heartRate != null && oxygenLevel != null && temperature != null && stressLevel != null) {
                                heartEntries.add(new Entry(currentDataIndex, heartRate));
                                spo2Entries.add(new Entry(currentDataIndex, oxygenLevel));
                                tempEntries.add(new Entry(currentDataIndex, temperature.floatValue()));
                                stressEntries.add(new Entry(currentDataIndex, stressLevel));

                                if (predictionEntries.size() < 10) {
                                    predictionEntries.add(0, heartRate.floatValue());
                                }

                                currentDataIndex++;
                            }
                        }

                        // Update UI on main thread
                        runOnUiThread(() -> {
                            if (!stressEntries.isEmpty()) {
                                float latestStressLevel = stressEntries.get(stressEntries.size() - 1).getY();
                                stressMeter.setProgress(latestStressLevel);
                                stressText.setText(String.format("Stress: %.0f%%", latestStressLevel));
                            }

                            // Make prediction if we have enough data
                            if (predictionEntries.size() == 10) {
                                predictHeartRate(predictionEntries);
                            }

                            // Update charts
                            updateChart(heartChart, heartEntries, "Heart Rate", heartText);
                            updateChart(spo2Chart, spo2Entries, "Oxygen Level", spo2Text);
                            updateChart(tempChart, tempEntries, "Temperature", tempText);
                        });
                    }).start();
                });
    }
    private void sendEmergencyNotification(String title, String message) {
        // Fetch the doctor's FCM token
        db.collection("doctor_collection").document(doctorId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String doctorFcmToken = document.getString("fcm_token");

                        if (doctorFcmToken != null) {
                            // Prepare notification payload
                            Map<String, String> notificationPayload = new HashMap<>();
                            notificationPayload.put("title", title);
                            notificationPayload.put("message", message);

                            // Trigger Firebase Cloud Function to send the notification
                            sendToFirebaseFunction(notificationPayload);
                        } else {
                            Log.e("FCM", "Doctor's FCM token is missing");
                        }
                    }
                });
    }
    private void predictHeartRate(List<Float> last10HeartRates) {
        if (last10HeartRates.size() != 10) return;

        try {
            float predictedROC = predictor.predict(last10HeartRates);
            float lastHR = last10HeartRates.get(9);

            MaxHRthreshold = lastHR * (1 + predictedROC);
            MinHRthreshold = lastHR * (1 - predictedROC);

            // Only check the newest point (currentDataIndex-1)
            boolean isAnomaly = (lastHR > MaxHRthreshold || lastHR < MinHRthreshold);
            isAnomalyDetected = isAnomaly;

            if (isAnomaly) {
                heartRateAnomalyTracker.markAnomaly(currentDataIndex - 1);
                Toast.makeText(this, "⚠️ Heart Rate Anomaly Detected!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Prediction error", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendToFirebaseFunction(Map<String, String> notificationPayload) {
        // Your Firebase Cloud Function code here to send the notification
        // This function should use Firebase Admin SDK to trigger the push notification
        // You can refer to the earlier Firebase Cloud Function setup for sending the notification to FCM
    }
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

    private void updateChart(LineChart chart, List<Entry> entries, String label, TextView textView) {

        // Add validation at start of method
        if (entries == null || entries.isEmpty()) {
            chart.clear();
            chart.invalidate();
            Log.w("ChartUpdate", "Empty data for " + label);
            return;
        }

        // Check for invalid entries
        for (Entry entry : entries) {
            if (Float.isNaN(entry.getX())){
                entry.setX(0);
            }
            if (Float.isNaN(entry.getY())) {
                entry.setY(0);
            }
        }
        if (entries.isEmpty()) {
            Log.w("ChartUpdate", "No data to plot for " + label);
            return;
        }

        // Clear existing data
        chart.clear();

        LineDataSet dataSet = new LineDataSet(entries, label);

        // Configure colors for each point based on its value
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float value = entry.getY();

            boolean isAnomaly = (i == entries.size() - 1) &&
                    (value > MaxHRthreshold || value < MinHRthreshold);

            if (label.contains("Heart")) {
                if (heartRateAnomalyTracker.isAnomaly(i) || ((value > MaxHRthreshold || value < MinHRthreshold))) {
                    colors.add(getColor(R.color.emergency));
                    textView.setTextColor(getColor(R.color.emergency));
                } else {
                    colors.add(getColor(R.color.healthy));
                    textView.setTextColor(getColor(R.color.healthy));
                }
            } else if (label.contains("Oxygen")) {
                if (value < 90) {
                    colors.add(getColor(R.color.emergency));
                    textView.setTextColor(getColor(R.color.emergency));
                } else if (value <= 94) {
                    colors.add(getColor(R.color.mild));
                    textView.setTextColor(getColor(R.color.mild));
                } else {
                    colors.add(getColor(R.color.healthy));
                    textView.setTextColor(getColor(R.color.healthy));
                }
            } else if (label.contains("Temperature")) {
                if (value > 40 || value < 35) {
                    colors.add(getColor(R.color.emergency));
                    textView.setTextColor(getColor(R.color.emergency));
                } else {
                    colors.add(getColor(R.color.healthy));
                    textView.setTextColor(getColor(R.color.healthy));
                }
            }
        }

        // Set the colors for each point
        dataSet.setCircleColors(colors);
        dataSet.setColor(getColor(R.color.baseline)); // Set the line color to a baseline color
        dataSet.setLineWidth(1f);
        dataSet.setCircleSize(2f);
        dataSet.setDrawValues(false);

        LineData data = new LineData(dataSet);
        chart.setData(data);

        // Handle axis shifting for MAX_POINTS
        if (entries.size() >= MAX_POINTS) {
            chart.getXAxis().setAxisMinimum(entries.size() - MAX_POINTS);
            chart.getXAxis().setAxisMaximum(entries.size());
        }

        chart.invalidate();
    }
}