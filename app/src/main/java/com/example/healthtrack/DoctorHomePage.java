package com.example.healthtrack;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

import java.util.ArrayList;
import java.util.List;

public class DoctorHomePage extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private LinearLayout patientListLayout;
    private String doctorId;
    private TextView drTitle;
    private Button refreshButton;

    private static final int MAX_POINTS = 25; // Consistent with PatientHealthData_nosensor

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_home_screen);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        patientListLayout = findViewById(R.id.patientListLayout);
        drTitle = findViewById(R.id.drTitle);
        refreshButton = findViewById(R.id.refreshButton);

        doctorId = mAuth.getCurrentUser().getUid();
        fetchDoctorLastName();
        fetchAssignedPatients();

        refreshButton.setOnClickListener(v -> refreshActivity());
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
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING) // Newest first
                .limit(MAX_POINTS)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e("Firestore", "Error fetching data", error);
                        return;
                    }

                    List<Entry> heartEntries = new ArrayList<>();
                    List<Entry> spo2Entries = new ArrayList<>();
                    List<Entry> tempEntries = new ArrayList<>(); // Temperature entries
                    List<Entry> stressEntries = new ArrayList<>(); // Stress level entries

                    // Iterate through the snapshots in reverse order (oldest first)
                    int index = 0;
                    List<DocumentSnapshot> documents = snapshots.getDocuments();
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
                                    heartEntries.add(new Entry(index, heartRate));
                                    spo2Entries.add(new Entry(index, oxygenLevel));
                                    tempEntries.add(new Entry(index, temperature.floatValue())); // Add temperature
                                    stressEntries.add(new Entry(index, stressLevel)); // Add stress level

                                    // Check for irregularities
                                    if (heartRate > 160 || heartRate < 50) {
                                        heartText.setTextColor(getColor(R.color.emergency));
                                    } else {
                                        heartText.setTextColor(getColor(R.color.healthy));
                                    }
                                    heartText.setText("BPM: " + heartRate);

                                    if (oxygenLevel < 90) {
                                        spo2Text.setTextColor(getColor(R.color.emergency));
                                    } else if (oxygenLevel <= 94) {
                                        spo2Text.setTextColor(getColor(R.color.mild));
                                    } else {
                                        spo2Text.setTextColor(getColor(R.color.healthy));
                                    }
                                    spo2Text.setText("O2: " + oxygenLevel + "%");

                                    // Update temperature text
                                    if (temperature > 40) {
                                        tempText.setTextColor(getColor(R.color.emergency));
                                    } else if (temperature < 35) {
                                        tempText.setTextColor(getColor(R.color.emergency));
                                    } else {
                                        tempText.setTextColor(getColor(R.color.healthy));
                                    }
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
                    Log.d("ChartData", "Heart Entries: " + heartEntries);
                    Log.d("ChartData", "Oxygen Entries: " + spo2Entries);
                    Log.d("ChartData", "Temperature Entries: " + tempEntries);
                    Log.d("ChartData", "Stress Entries: " + stressEntries);

                    // Update charts only if data is valid
                    if (!heartEntries.isEmpty() && !spo2Entries.isEmpty() && !tempEntries.isEmpty() && !stressEntries.isEmpty()) {
                        updateChart(heartChart, heartEntries, "Heart Rate", heartText);
                        updateChart(spo2Chart, spo2Entries, "Oxygen Level", spo2Text);
                        updateChart(tempChart, tempEntries, "Temperature", tempText); // Update temperature chart
                    } else {
                        Log.w("Firestore", "No valid data to plot.");
                    }
                });
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
                if (value > 160 || value < 50) {
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
}