package com.example.healthtrack;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class PatientDetailActivity extends AppCompatActivity {
    private RecyclerView warningRecyclerView;
    private WarningAdapter warningAdapter;
    private FirebaseFirestore db;
    private String patientId;
    private static final int MAX_POINTS = 25; // Consistent with PatientHealthData_nosensor
    private boolean isAnomalyDetected = false;
    private TextView heartText;
    private TextView spo2Text;
    private TextView tempText;
    private TextView stressText;
    private SemiCircleMeter stressMeter;
    private Button predictButton;
    private HeartRatePredictor predictor;
    float HRthreshold;
    float predictedROC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_detail);

        db = FirebaseFirestore.getInstance();

        // Retrieve the patientId from the Intent
        patientId = getIntent().getStringExtra("patientId");
        if (patientId == null) {
            Toast.makeText(this, "Error: Patient ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        heartText = findViewById(R.id.heartRateTextView);
        spo2Text = findViewById(R.id.OxiTextView);
        tempText = findViewById(R.id.tempTextView);
        stressText = findViewById(R.id.stressTextView);
        stressMeter = findViewById(R.id.stressMeter);
        predictButton = findViewById(R.id.predictButton);

        // Initialize the predictor
        predictor = new HeartRatePredictor(this);

        // Initialize RecyclerView for warnings
        warningRecyclerView = findViewById(R.id.warningRecyclerView);
        warningRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        warningAdapter = new WarningAdapter(new ArrayList<>());
        warningRecyclerView.setAdapter(warningAdapter);

        // Fetch patient details and data
        fetchPatientDetails();
        fetchPatientGraphs();
        fetchAndDisplayWarnings();
    }

    private void predictHeartRate(List<Float> last10HeartRates) {
        if (last10HeartRates.size() != 10) {
            Log.e("PredictHeartRate", "Insufficient data for prediction");
            return;
        }

        try {
            predictedROC = predictor.predict(last10HeartRates); // Get predicted rate of change
            float lastHR = last10HeartRates.get(9); // Most recent heart rate

            // Calculate threshold: HR should not exceed lastHR * (1 + predictedROC)
            HRthreshold = lastHR * (1 + predictedROC);
            boolean anomalyDetected = false;

            // Check if any recent HR reading exceeds the threshold
            for (Float hr : last10HeartRates) {
                if (hr > HRthreshold) {
                    anomalyDetected = true;
                    break;
                }
            }

            // Update global anomaly status
            isAnomalyDetected = anomalyDetected;

            // Show results
            if (isAnomalyDetected) {
                Toast.makeText(this, "⚠️ Heart Rate Anomaly Detected!", Toast.LENGTH_LONG).show();
                Log.w("AnomalyDetection", "Anomaly detected! Last HR: " + lastHR + " Threshold: " + HRthreshold);
                heartText.setTextColor(getColor(R.color.emergency)); // Highlight HR text
            } else {
                heartText.setTextColor(getColor(R.color.healthy));
            }

            Log.w("Machine Learning Heart Rate", String.format("Predicted ROC: %.2f | Threshold: %.1f BPM", predictedROC, HRthreshold));
        } catch (Exception e) {
            Toast.makeText(this, "Error making prediction: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (predictor != null) {
            predictor.close();
        }
    }

    private void fetchPatientDetails() {
        db.collection("patient_collection").document(patientId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String patientName = document.getString("first_name") + " " + document.getString("last_name");
                            fetchPatientGraphs();
                        }
                    } else {
                        Log.w("PatientDetailActivity", "Error fetching patient details", task.getException());
                    }
                });
    }

    private void fetchPatientGraphs() {
        db.collection("patient_collection").document(patientId)
                .collection("health_records")
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING) // Newest first
                .limit(MAX_POINTS)
                .addSnapshotListener((snapshots, error) -> {
                    if (snapshots != null && !snapshots.isEmpty()) {
                        List<Entry> heartRateEntries = new ArrayList<>();
                        List<Entry> oxygenLevelEntries = new ArrayList<>();
                        List<Entry> temperatureEntries = new ArrayList<>();
                        List<Entry> stressLevelEntries = new ArrayList<>();
                        List<Float> predictionEntries = new ArrayList<>(); // Store last 10 for prediction

                        List<DocumentSnapshot> documents = snapshots.getDocuments();

                        // Iterate through the snapshots in reverse order (oldest first)
                        int index = 0;
                        for (int i = documents.size() - 1; i >= 0; i--) {
                            DocumentSnapshot document = documents.get(i);
                            Long heartRate = document.getLong("heartRate");
                            Long oxygenLevel = document.getLong("oxygenLevel");
                            Double temperature = document.getDouble("temperature");
                            Long stressLevel = document.getLong("stressLevel");

                            if (heartRate != null && oxygenLevel != null && temperature != null && stressLevel != null) {
                                heartRateEntries.add(new Entry(index, heartRate));
                                oxygenLevelEntries.add(new Entry(index, oxygenLevel));
                                temperatureEntries.add(new Entry(index, temperature.floatValue()));
                                stressLevelEntries.add(new Entry(index, stressLevel));

                                // Collect last 10 readings for prediction
                                predictionEntries.add(heartRate.floatValue());
                                if (predictionEntries.size() > 10) {
                                    predictionEntries.remove(0); // Keep only the last 10
                                }

                                index++;
                            }
                        }

                        // Ensure we have exactly 10 readings before making a prediction
                        if (predictionEntries.size() == 10) {
                            predictHeartRate(predictionEntries);
                        }

                        // Log final entries
                        Log.d("ChartData", "Heart Entries: " + heartRateEntries);
                        Log.d("ChartData", "Oxygen Entries: " + oxygenLevelEntries);
                        Log.d("ChartData", "Temperature Entries: " + temperatureEntries);
                        Log.d("ChartData", "Stress Entries: " + stressLevelEntries);

                        // Display graphs
                        LineChart heartChart = findViewById(R.id.heartChart);
                        LineChart spo2Chart = findViewById(R.id.spo2Chart);
                        LineChart tempChart = findViewById(R.id.tempChart); // Temperature chart

                        configureChart(heartChart, 200f); // Heart rate chart
                        configureChart(spo2Chart, 100f);  // Oxygen level chart
                        configureChart(tempChart, 50f);   // Temperature chart

                        updateChart(heartChart, heartRateEntries, "Heart Rate", heartText);
                        updateChart(spo2Chart, oxygenLevelEntries, "Oxygen Level", spo2Text);
                        updateChart(tempChart, temperatureEntries, "Temperature", tempText); // Update temperature chart

                        // Set click listeners for charts
                        heartChart.setOnClickListener(v -> {
                            Intent intent = new Intent(this, GraphDetailActivity.class);
                            intent.putExtra("patientId", patientId);
                            intent.putExtra("graphType", "heartRate");
                            startActivity(intent);
                        });

                        spo2Chart.setOnClickListener(v -> {
                            Intent intent = new Intent(this, GraphDetailActivity.class);
                            intent.putExtra("patientId", patientId);
                            intent.putExtra("graphType", "oxygenLevel");
                            startActivity(intent);
                        });

                        tempChart.setOnClickListener(v -> {
                            Intent intent = new Intent(this, GraphDetailActivity.class);
                            intent.putExtra("patientId", patientId);
                            intent.putExtra("graphType", "temperature");
                            startActivity(intent);
                        });
                    }
                });
    }

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
    }

    private void updateChart(LineChart chart, List<Entry> entries, String label, TextView textView) {
        if (entries.isEmpty()) {
            Log.w("ChartUpdate", "No data to plot for " + label);
            return;
        }

        // Clear existing data
        chart.clear();

        LineDataSet dataSet = new LineDataSet(entries, label);

        // Configure colors for each point based on its value
        List<Integer> colors = new ArrayList<>();
        for (Entry entry : entries) {
            float value = entry.getY();
            if (label.contains("Heart")) {
                if (value > HRthreshold) {
                    colors.add(getColor(R.color.emergency));
                } else {
                    colors.add(getColor(R.color.healthy));
                }
            } else if (label.contains("Oxygen")) {
                if (value < 90) {
                    colors.add(getColor(R.color.emergency));
                } else if (value <= 94) {
                    colors.add(getColor(R.color.mild));
                } else {
                    colors.add(getColor(R.color.healthy));
                }
            } else if (label.contains("Temperature")) {
                if (value > 40 || value < 35) {
                    colors.add(getColor(R.color.emergency));
                } else {
                    colors.add(getColor(R.color.healthy));
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
    private void fetchAndDisplayWarnings() {
        db.collection("patient_collection")
                .document(patientId)
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
                            long timestamp = System.currentTimeMillis();
                            for (String warningMessage : warnings) {
                                warningList.add(new Warning(warningMessage, timestamp));
                            }
                        }

                        // Update RecyclerView adapter
                        warningAdapter.setWarnings(warningList);
                    } else {
                        Log.e("Warnings", "Error fetching real-time warnings", error);
                    }
                });
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