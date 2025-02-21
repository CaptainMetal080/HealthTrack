package com.example.healthtrack;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class PatientHealthData_nosensor extends AppCompatActivity {
    private static final int MAX_POINTS = 25;

    private LineChart heartChart;
    private LineChart spo2Chart;

    private LineDataSet heartRateDataSet;
    private LineDataSet oxygenDataSet;

    private static final int REQUEST_CALL_PHONE_PERMISSION = 2;
    TextView heartRateView;
    TextView spo2View;
    private int heartRateIndex;
    private int oxygenIndex;

    private int oxygenLevel;
    private int heartRate;
    private DataUploader uploader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overall_patient_health);  // Make sure this is correct
        heartRateView = findViewById(R.id.heartRateTextView);
        spo2View = findViewById(R.id.OxiTextView);
        heartRateIndex = 0;
        oxygenIndex = 0;
        heartChart = findViewById(R.id.heartGraph);
        spo2Chart = findViewById(R.id.spo2Graph);

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
        configureChart(heartChart, 200);
        configureChart(spo2Chart, 100);

        // Simulate data updates for testing
        simulateDataUpdates();
    }

    private void simulateDataUpdates() {
        Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // Simulate heart rate and oxygen level data
                heartRate = (int) (Math.random() * 100 + 60); // Random heart rate between 60-160
                oxygenLevel = (int) (Math.random() * 20 + 80); // Random oxygen level between 80-100

                // Update UI with simulated data
                updateUI(heartRate, oxygenLevel);

                // Schedule the next update
                handler.postDelayed(this, 2000); // Update every 2 seconds
            }
        };
        handler.post(runnable); // Start the simulation
    }

    private void updateUI(int heartRate, int oxygenLevel) {
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

            // Upload data to Firestore
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedDate = now.format(formatter);
            String pId = getIntent().getStringExtra("uid");

            PatientData patientData = new PatientData(pId, formattedDate, heartRate, oxygenLevel);
            uploader.uploadPatientData(patientData);
        });
    }

    private void callEmergency() {
        // Show confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Emergency Call");
        builder.setMessage("Critical Health condition detected. Call emergency?");

        // Positive button to call emergency
        builder.setPositiveButton("Call", (dialog, id) -> Call());

        // Negative button to cancel
        builder.setNegativeButton("Cancel", (dialog, id) -> dialog.dismiss());

        // Create and show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();

        // Automatically proceed with the call after 5 seconds if the user doesn't interact
        new Handler().postDelayed(() -> {
            if (dialog.isShowing()) {
                Call();
            }
        }, 5000); // Delay of 5 seconds
    }

    private void Call() {
        try {
            // Make the call
            Intent phoneIntent = new Intent(Intent.ACTION_CALL);
            phoneIntent.setData(Uri.parse("tel:1234567890"));  // Replace with actual number
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

    private void updateChart(LineChart chart, LineDataSet dataSet, int value, int index) {
        // Add a new data point to the dataset
        dataSet.addEntry(new Entry(index, value));

        // Keep only the last MAX_POINTS points in the chart
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
        chart.moveViewToX(data.getEntryCount()); // Move the chart to the latest entry
        chart.invalidate(); // Refresh the chart
    }
}