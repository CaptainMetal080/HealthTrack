package com.example.healthtrack;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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

    private FirebaseFirestore db;
    private String patientId;
    private static final int MAX_POINTS = 25; // Consistent with PatientHealthData_nosensor

    private TextView heartText;
    private TextView spo2Text;
    private TextView tempText;
    private TextView stressText;
    private SemiCircleMeter stressMeter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_detail);

        db = FirebaseFirestore.getInstance();

        // Retrieve the patientId from the Intent
        patientId = getIntent().getStringExtra("patientId");
        if (patientId == null) {
            Log.e("PatientDetailActivity", "No patientId found in Intent");
            finish(); // Close the activity if no patientId is found
            return;
        }

        // Log the patientId for debugging
        Log.d("PatientDetailActivity", "Received patientId: " + patientId);

        // Initialize views
        heartText = findViewById(R.id.heartRateTextView);
        spo2Text = findViewById(R.id.OxiTextView);
        tempText = findViewById(R.id.tempTextView);
        stressText = findViewById(R.id.stressTextView);
        stressMeter = findViewById(R.id.stressMeter); // Stress meter

        // Fetch patient details and graphs
        fetchPatientDetails();
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
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Entry> heartRateEntries = new ArrayList<>();
                        List<Entry> oxygenLevelEntries = new ArrayList<>();
                        List<Entry> temperatureEntries = new ArrayList<>(); // Temperature entries
                        List<Entry> stressLevelEntries = new ArrayList<>(); // Stress level entries

                        // Iterate through the snapshots in reverse order (oldest first)
                        int index = 0;
                        List<DocumentSnapshot> documents = task.getResult().getDocuments();
                        for (int i = documents.size() - 1; i >= 0; i--) {
                            DocumentSnapshot document = documents.get(i);

                            // Cast DocumentSnapshot to QueryDocumentSnapshot
                            if (document instanceof QueryDocumentSnapshot) {
                                QueryDocumentSnapshot queryDocument = (QueryDocumentSnapshot) document;

                                // Validate data before adding to entries
                                Long heartRate = queryDocument.getLong("heartRate");
                                Long oxygenLevel = queryDocument.getLong("oxygenLevel");
                                Double temperature = queryDocument.getDouble("temperature"); // Fetch temperature
                                Long stressLevel = queryDocument.getLong("stressLevel"); // Fetch stress level

                                if (heartRate != null && oxygenLevel != null && temperature != null && stressLevel != null) {
                                    // Ensure valid values
                                    if (heartRate >= 0 && oxygenLevel >= 0 && temperature >= 0 && stressLevel >= 0) {
                                        heartRateEntries.add(new Entry(index, heartRate));
                                        oxygenLevelEntries.add(new Entry(index, oxygenLevel));
                                        temperatureEntries.add(new Entry(index, temperature.floatValue())); // Add temperature
                                        stressLevelEntries.add(new Entry(index, stressLevel)); // Add stress level

                                        // Check for irregularities
                                        if (heartRate > 160 || heartRate < 50) {
                                            heartText.setTextColor(getColor(R.color.emergency));
                                        } else {
                                            heartText.setTextColor(getColor(R.color.healthy));
                                        }

                                        if (oxygenLevel < 90) {
                                            spo2Text.setTextColor(getColor(R.color.emergency));
                                        } else if (oxygenLevel <= 94) {
                                            spo2Text.setTextColor(getColor(R.color.mild));
                                        } else {
                                            spo2Text.setTextColor(getColor(R.color.healthy));
                                        }

                                        // Update temperature text
                                        tempText.setText(String.format("Temp: %.1f°C", temperature));

                                        // Update stress meter
                                        stressMeter.setProgress(stressLevel.floatValue()); // Update stress meter
                                        stressText.setText(String.format("Stress: %.0f", stressLevel.floatValue()));

                                        index++;
                                    }
                                }
                            }
                        }

                        // Log the final entries
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
                    } else {
                        Log.w("PatientDetailActivity", "Error fetching health records", task.getException());
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

        // Configure colors based on latest value
        if (!entries.isEmpty()) {
            float lastValue = entries.get(entries.size() - 1).getY();
            if (label.contains("Heart")) {
                if (lastValue > 160 || lastValue < 50) {
                    dataSet.setColor(getColor(R.color.emergency));
                    dataSet.setCircleColor(getColor(R.color.emergency));
                } else {
                    dataSet.setColor(getColor(R.color.healthy));
                    dataSet.setCircleColor(getColor(R.color.healthy));
                }
                textView.setText(String.format("BPM: %.0f", lastValue));
            } else if (label.contains("Oxygen")) {
                if (lastValue < 90) {
                    dataSet.setColor(getColor(R.color.emergency));
                    dataSet.setCircleColor(getColor(R.color.emergency));
                } else if (lastValue <= 94) {
                    dataSet.setColor(getColor(R.color.mild));
                    dataSet.setCircleColor(getColor(R.color.mild));
                } else {
                    dataSet.setColor(getColor(R.color.healthy));
                    dataSet.setCircleColor(getColor(R.color.healthy));
                }
                textView.setText(String.format("O2: %.0f%%", lastValue));
            } else if (label.contains("Temperature")) {
                textView.setText(String.format("Temp: %.1f°C", lastValue));
            }
        }

        dataSet.setDrawValues(false);
        dataSet.setCircleSize(3f);
        dataSet.setLineWidth(2f);

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