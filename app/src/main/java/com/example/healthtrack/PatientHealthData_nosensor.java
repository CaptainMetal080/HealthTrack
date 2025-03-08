package com.example.healthtrack;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


public class PatientHealthData_nosensor extends AppCompatActivity {
    private static final int MAX_POINTS = 25;

    private LineChart heartChart;
    private LineChart spo2Chart;
    private LineChart tempChart;

    private LineDataSet heartRateDataSet;
    private LineDataSet oxygenDataSet;
    private LineDataSet tempDataSet; // Temperature data set

    TextView heartRateView;
    TextView spo2View;
    TextView tempView; // Temperature text view
    TextView stressTextView; // Stress text view

    private int heartRateIndex;
    private int oxygenIndex;
    private int tempIndex; // Temperature index
    private FirebaseFirestore db;

    private int oxygenLevel;
    private int heartRate;
    private float temperature; // Temperature value
    private int stressLevel; // Stress level (0 to 100)

    private DataUploader uploader;

    private SemiCircleMeter stressMeter; // Stress meter

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_patient_health);

        // Initialize DataUploader
        uploader = new DataUploader(this);
        db = FirebaseFirestore.getInstance();
        // Initialize views
        heartRateView = findViewById(R.id.heartRateTextView);
        spo2View = findViewById(R.id.OxiTextView);
        tempView = findViewById(R.id.TempTextView); // Temperature text view
        stressTextView = findViewById(R.id.stressTextView); // Stress text view
        stressMeter = findViewById(R.id.stressMeter); // Stress meter

        heartRateIndex = 0;
        oxygenIndex = 0;
        tempIndex = 0;

        // Initialize charts
        heartChart = findViewById(R.id.heartGraph);
        spo2Chart = findViewById(R.id.spo2Graph);
        tempChart = findViewById(R.id.tempGraph); // Temperature chart

        // Initialize data sets
        heartRateDataSet = new LineDataSet(new ArrayList<>(), "Heart Rate");
        oxygenDataSet = new LineDataSet(new ArrayList<>(), "Oxygen Level");
        tempDataSet = new LineDataSet(new ArrayList<>(), "Temperature"); // Temperature data set

        // Set line styles (optional)
        heartRateDataSet.setColor(getColor(R.color.healthy));
        heartRateDataSet.setCircleColor(getColor(R.color.healthy));
        oxygenDataSet.setColor(getColor(R.color.healthy));
        oxygenDataSet.setCircleColor(getColor(R.color.healthy));
        tempDataSet.setColor(getColor(R.color.healthy)); // Temperature line color
        tempDataSet.setCircleColor(getColor(R.color.healthy));

        // Create LineData objects
        LineData heartRateData = new LineData(heartRateDataSet);
        LineData oxygenData = new LineData(oxygenDataSet);
        LineData tempData = new LineData(tempDataSet); // Temperature data

        // Set data to charts
        heartChart.setData(heartRateData);
        spo2Chart.setData(oxygenData);
        tempChart.setData(tempData); // Set temperature data

        // Configure the charts
        configureChart(heartChart, 200); // Heart rate chart
        configureChart(spo2Chart, 100);  // Oxygen level chart
        configureChart(tempChart, 50);   // Temperature chart (adjust max value as needed)

        // Simulate data updates for testing
        simulateDataUpdates();

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
    private void simulateDataUpdates() {
        Handler handler = new Handler();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // Simulate heart rate, oxygen level, temperature, and stress level data
                heartRate = (int) (Math.random() * 100 + 60); // Random heart rate between 60-160
                oxygenLevel = (int) (Math.random() * 20 + 80); // Random oxygen level between 80-100
                temperature = (float) (Math.random() * 7 + 35); // Random temperature between 35-45°C
                stressLevel = (int) (Math.random() * 100); // Random stress level between 0-100

                // Update UI with simulated data
                updateUI(heartRate, oxygenLevel, temperature, stressLevel);

                // Schedule the next update
                handler.postDelayed(this, 2000); // Update every 2 seconds
            }
        };
        handler.post(runnable); // Start the simulation
    }

    private void updateUI(int heartRate, int oxygenLevel, float temperature, int stressLevel) {
        runOnUiThread(() -> {
            // Update heart rate
            if (heartRate > 160) {
                Toast.makeText(PatientHealthData_nosensor.this, "Critical: High Heart Rate!", Toast.LENGTH_SHORT).show();
                heartRateView.setTextColor(getColor(R.color.emergency));
                heartRateDataSet.setColor(getColor(R.color.emergency));
                heartRateDataSet.setCircleColor(getColor(R.color.emergency));
                callEmergency();
            } else if (heartRate < 50) {
                Toast.makeText(PatientHealthData_nosensor.this, "Critical: Slow Heart Rate!", Toast.LENGTH_SHORT).show();
                heartRateView.setTextColor(getColor(R.color.emergency));
                heartRateDataSet.setColor(getColor(R.color.emergency));
                heartRateDataSet.setCircleColor(getColor(R.color.emergency));
                callEmergency();
            } else {
                heartRateDataSet.setColor(getColor(R.color.healthy));
                heartRateDataSet.setCircleColor(getColor(R.color.healthy));
                heartRateView.setTextColor(getColor(R.color.healthy));
            }
            heartRateView.setText("BPM: " + heartRate + " bpm");
            updateChart(heartChart, heartRateDataSet, heartRate, heartRateIndex);
            heartRateIndex++;

            // Update oxygen level
            if (oxygenLevel < 90) {
                Toast.makeText(PatientHealthData_nosensor.this, "Critical: Low SpO2 detected!", Toast.LENGTH_SHORT).show();
                spo2View.setTextColor(getColor(R.color.emergency));
                oxygenDataSet.setColor(getColor(R.color.emergency));
                oxygenDataSet.setCircleColor(getColor(R.color.emergency));
                callEmergency();
            } else if (oxygenLevel <= 94) {
                Toast.makeText(PatientHealthData_nosensor.this, "Warning: Mildly low SpO2 detected!", Toast.LENGTH_SHORT).show();
                oxygenDataSet.setColor(getColor(R.color.mild));
                oxygenDataSet.setCircleColor(getColor(R.color.mild));
                spo2View.setTextColor(getColor(R.color.mild));
            } else {
                oxygenDataSet.setColor(getColor(R.color.healthy));
                oxygenDataSet.setCircleColor(getColor(R.color.healthy));
                spo2View.setTextColor(getColor(R.color.healthy));
            }
            spo2View.setText("O2: " + oxygenLevel + "%");
            updateChart(spo2Chart, oxygenDataSet, oxygenLevel, oxygenIndex);
            oxygenIndex++;

            // Update temperature
            if (temperature > 40) {
                Toast.makeText(PatientHealthData_nosensor.this, "Critical: High Temperature!", Toast.LENGTH_SHORT).show();
                tempView.setTextColor(getColor(R.color.emergency));
                tempDataSet.setColor(getColor(R.color.emergency));
                tempDataSet.setCircleColor(getColor(R.color.emergency));
            } else if (temperature < 35) {
                Toast.makeText(PatientHealthData_nosensor.this, "Critical: Low Temperature!", Toast.LENGTH_SHORT).show();
                tempView.setTextColor(getColor(R.color.emergency));
                tempDataSet.setColor(getColor(R.color.emergency));
                tempDataSet.setCircleColor(getColor(R.color.emergency));
            } else {
                tempDataSet.setColor(getColor(R.color.healthy));
                tempDataSet.setCircleColor(getColor(R.color.healthy));
                tempView.setTextColor(getColor(R.color.healthy));
            }
            tempView.setText("Temp: " + temperature + " °C");
            updateChart(tempChart, tempDataSet, temperature, tempIndex);
            tempIndex++;

            // Update stress level
            stressTextView.setText("Stress: " + stressLevel);
            stressMeter.setProgress(stressLevel); // Update the stress meter

            // Upload data to Firestore
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedDate = now.format(formatter);

            // Get UID from FirebaseAuth
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // Create PatientData object with all fields
            PatientData patientData = new PatientData(formattedDate, heartRate, oxygenLevel, temperature, stressLevel);
            uploader.uploadPatientData(uid, patientData);
        });
    }

    private void callEmergency() {
//        // Show confirmation dialog
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Emergency Call");
//        builder.setMessage("Critical Health condition detected. Call emergency?");
//
//        // Positive button to call emergency
//        builder.setPositiveButton("Call", (dialog, id) -> Call());
//
//        // Negative button to cancel
//        builder.setNegativeButton("Cancel", (dialog, id) -> dialog.dismiss());
//
//        // Create and show the dialog
//        AlertDialog dialog = builder.create();
//        dialog.show();
//
//        // Automatically proceed with the call after 5 seconds if the user doesn't interact
//        new Handler().postDelayed(() -> {
//            if (dialog.isShowing()) {
//                Call();
//            }
//        }, 5000); // Delay of 5 seconds
    }

    private void Call() {
        try {
            // Make the call
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
        // Add a new data point to the dataset
        dataSet.addEntry(new Entry(index, value));

        // Configure colors for each point based on its value
        List<Integer> colors = new ArrayList<>();
        for (Entry entry : dataSet.getValues()) {
            float entryValue = entry.getY();
            if (dataSet.getLabel().contains("Heart")) {
                if (entryValue > 160 || entryValue < 50) {
                    colors.add(getColor(R.color.emergency));
                } else {
                    colors.add(getColor(R.color.healthy));
                }
            } else if (dataSet.getLabel().contains("Oxygen")) {
                if (entryValue < 90) {
                    colors.add(getColor(R.color.emergency));
                } else if (entryValue <= 94) {
                    colors.add(getColor(R.color.mild));
                } else {
                    colors.add(getColor(R.color.healthy));
                }
            } else if (dataSet.getLabel().contains("Temperature")) {
                if (entryValue > 40 || entryValue < 35) {
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

        // Keep only the last MAX_POINTS points in the chart
        if (dataSet.getEntryCount() > MAX_POINTS) {
            chart.getXAxis().setAxisMinimum(index - MAX_POINTS + 1); // Shift the axis left
            chart.getXAxis().setAxisMaximum(index + 1);  // Remove the oldest point
        }

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
        chart.moveViewToX(data.getEntryCount()); // Move the chart to the latest entry
        chart.invalidate(); // Refresh the chart
    }
}