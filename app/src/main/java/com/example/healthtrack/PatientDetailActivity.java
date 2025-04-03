package com.example.healthtrack;

import android.annotation.SuppressLint;
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
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
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
    float MaxHRthreshold;
    float MinHRthreshold;
    float predictedROC;
    private int currentDataIndex = 0;

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
        fetchAndUpdateStressLevel(stressText, stressMeter);
        fetchAndDisplayWarnings();
    }

    private void predictHeartRate(List<Float> last10HeartRates) {
        if (last10HeartRates.size() != 10) {
            Log.e("PredictHeartRate", "Insufficient data for prediction");
            return;
        }

        try {
            float predictedROC = predictor.predict(last10HeartRates); // Get predicted rate of change
            float lastHR = last10HeartRates.get(9); // Most recent heart rate

            // Calculate threshold: HR should not exceed lastHR * (1 + predictedROC)
            MaxHRthreshold = lastHR * (1 + predictedROC);
            MinHRthreshold = lastHR * (1 - predictedROC);
            boolean anomalyDetected = false;

            // Check if any recent HR reading exceeds the threshold
            for (Float hr : last10HeartRates) {
                if (hr > MaxHRthreshold || hr<MinHRthreshold) {
                    anomalyDetected = true;
                    break;
                }
            }

            // Update global anomaly status
            isAnomalyDetected = anomalyDetected;

            // Show results
            if (isAnomalyDetected) {
                Toast.makeText(this, "⚠️ Heart Rate Anomaly Detected!", Toast.LENGTH_LONG).show();
                Log.w("AnomalyDetection", "Anomaly detected! Last HR: " + lastHR + " Threshold: " + MaxHRthreshold);
                heartText.setTextColor(getColor(R.color.emergency)); // Highlight HR text
            } else {
                heartText.setTextColor(getColor(R.color.healthy));
            }

            Log.w("Machine Learning Heart Rate", String.format("Predicted ROC: %.2f | Threshold: %.1f BPM", predictedROC, MaxHRthreshold));
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
    private void fetchAndUpdateStressLevel(TextView stressTextView, SemiCircleMeter stressMeter) {
        db.collection("patient_collection")
                .document(patientId)
                .collection("health_records")
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot latestRecord = task.getResult().getDocuments().get(0);
                        Long stressLevel = latestRecord.getLong("stressLevel");

                        if (stressLevel != null) {
                            // Update the stress TextView and SemiCircleMeter
                            stressTextView.setText("Stress: " + stressLevel + "%");
                            stressMeter.setProgress(stressLevel.floatValue());
                        }
                    } else {
                        Log.e("Firestore", "Failed to fetch latest stress level", task.getException());
                    }
                });
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
                        List<Entry> heartRateEntries = new ArrayList<>();
                        List<Entry> oxygenLevelEntries = new ArrayList<>();
                        List<Entry> temperatureEntries = new ArrayList<>();
                        List<Entry> stressLevelEntries = new ArrayList<>();
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
                                heartRateEntries.add(new Entry(currentDataIndex, heartRate));
                                oxygenLevelEntries.add(new Entry(currentDataIndex, oxygenLevel));
                                temperatureEntries.add(new Entry(currentDataIndex, temperature.floatValue()));
                                stressLevelEntries.add(new Entry(currentDataIndex, stressLevel));

                                if (predictionEntries.size() < 10) {
                                    predictionEntries.add(0, heartRate.floatValue());
                                }

                                currentDataIndex++;
                            }
                        }


                        // Update UI on main thread
                        runOnUiThread(() -> {
                            // Make prediction if we have enough data
                            if (predictionEntries.size() == 10) {
                                predictHeartRate(predictionEntries);
                            }

                            // Update text views with latest values
                            if (!heartRateEntries.isEmpty()) {
                                float latestHeartRate = heartRateEntries.get(heartRateEntries.size() - 1).getY();
                                heartText.setText("BPM: " + (int) latestHeartRate);
                            }

                            if (!oxygenLevelEntries.isEmpty()) {
                                float latestOxygenLevel = oxygenLevelEntries.get(oxygenLevelEntries.size() - 1).getY();
                                spo2Text.setText("O2: " + (int) latestOxygenLevel + "%");
                            }

                            if (!temperatureEntries.isEmpty()) {
                                float latestTemperature = temperatureEntries.get(temperatureEntries.size() - 1).getY();
                                tempText.setText("Temp: " + String.format("%.1f", latestTemperature) + "°C");
                            }

                            // Get chart references and configure them
                            LineChart heartChart = findViewById(R.id.heartChart);
                            LineChart spo2Chart = findViewById(R.id.spo2Chart);
                            LineChart tempChart = findViewById(R.id.tempChart);

                            configureChart(heartChart, 200f);
                            configureChart(spo2Chart, 100f);
                            configureChart(tempChart, 50f);

                            // Update charts
                            updateChart(heartChart, heartRateEntries, "Heart Rate", heartText);
                            updateChart(spo2Chart, oxygenLevelEntries, "Oxygen Level", spo2Text);
                            updateChart(tempChart, temperatureEntries, "Temperature", tempText);

                            // Set click listeners (unchanged from original)
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
                        });
                    }).start();
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


        if (chart.getId() == R.id.heartChart) {
            LimitLine upperLimit = new LimitLine(MaxHRthreshold, "Upper Threshold");
            upperLimit.setLineColor(Color.RED);
            upperLimit.setLineWidth(1f);
            upperLimit.setTextColor(Color.BLACK);
            upperLimit.setTextSize(10f);

            LimitLine lowerLimit = new LimitLine(MinHRthreshold, "Lower Threshold");
            lowerLimit.setLineColor(Color.RED);
            lowerLimit.setLineWidth(1f);
            lowerLimit.setTextColor(Color.BLACK);
            lowerLimit.setTextSize(10f);

            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.addLimitLine(upperLimit);
            leftAxis.addLimitLine(lowerLimit);
        }

    }

    @SuppressLint("ResourceAsColor")
    private void updateChart(LineChart chart, List<Entry> entries, String label, TextView textView) {
        if (entries == null || entries.isEmpty()) {
            chart.clear();
            chart.invalidate();
            Log.w("ChartUpdate", "No data to plot for " + label);
            return;
        }

        // Create new list with properly indexed entries
        List<Entry> validEntries = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (!Float.isNaN(entry.getY()) && !Float.isInfinite(entry.getY())) {
                validEntries.add(new Entry(i, entry.getY())); // Reindex properly
            }
        }

        if (validEntries.isEmpty()) {
            chart.clear();
            chart.invalidate();
            return;
        }

        LineDataSet dataSet = new LineDataSet(validEntries, label);

        // Configure colors
        List<Integer> colors = new ArrayList<>();
        int textColor = R.color.healthy;

        for (int i = 0; i < validEntries.size(); i++) {
            float value = validEntries.get(i).getY();

            if (label.contains("Heart")) {
                if (value > MaxHRthreshold || value < MinHRthreshold) {
                    colors.add(getColor(R.color.emergency));
                    textView.setTextColor(R.color.emergency);
                } else {
                    colors.add(getColor(R.color.healthy));
                    textView.setTextColor(R.color.healthy);
                }
            } else if (label.contains("Oxygen")) {
                if (value < 90) {
                    colors.add(getColor(R.color.emergency));
                    textView.setTextColor(R.color.emergency);
                } else if (value <= 94) {
                    colors.add(getColor(R.color.mild));
                    textView.setTextColor(R.color.mild);
                } else {
                    colors.add(getColor(R.color.healthy));
                    textView.setTextColor(R.color.healthy);
                }
            } else if (label.contains("Temperature")) {
                if (value > 40 || value < 35) {
                    colors.add(getColor(R.color.emergency));
                    textView.setTextColor(R.color.emergency);
                } else {
                    colors.add(getColor(R.color.healthy));
                    textView.setTextColor(R.color.healthy);
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
                            String latestTimestamp = patientDataList.get(0).getDatetime_captured();
                            long timestamp = convertDateStringToTimestamp(latestTimestamp);
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