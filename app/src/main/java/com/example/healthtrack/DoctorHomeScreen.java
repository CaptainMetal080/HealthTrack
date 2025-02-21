package com.example.healthtrack;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class DoctorHomeScreen extends AppCompatActivity {
    private List<PatientData> patientDataList;
    private Handler handler;
    private final int FETCH_INTERVAL = 110000; // 110 seconds

    private LineChart heartChart;
    private LineChart spo2Chart;

    private TextView heartText;
    private TextView spo2Text;

    private int MAX_POINTS = 40;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_screen);

        patientDataList = new ArrayList<>();
        handler = new Handler();

        // Firebase Realtime Database reference
        databaseReference = FirebaseDatabase.getInstance().getReference("patient_collection");

        String lastName = getIntent().getStringExtra("lastName");
        TextView doctorNameTextView = findViewById(R.id.drTitle);
        doctorNameTextView.setText("Welcome, Dr. " + lastName);

        startFetchingData();
    }

    private void startFetchingData() {
        handler.post(fetchDataRunnable);
    }

    private final Runnable fetchDataRunnable = new Runnable() {
        @Override
        public void run() {
            fetchHealthData();

            // Schedule the next fetch after the interval
            handler.postDelayed(this, FETCH_INTERVAL);
        }
    };

    public void fetchHealthData() {
        // Fetch data for all patients or specific patientId if needed
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                patientDataList.clear(); // Clear previous data
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    PatientData patientData = snapshot.getValue(PatientData.class);
                    patientDataList.add(patientData);
                }

                if (!patientDataList.isEmpty()) {
                    plotHealthData();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("DataFetcher", "Error fetching data: " + databaseError.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop fetching data when the activity is destroyed
        if (handler != null) {
            handler.removeCallbacks(fetchDataRunnable);
        }
    }

    @SuppressLint("DefaultLocale")
    private void plotHealthData() {
        // Prepare data for the charts (e.g., heart rate or oxygen levels)
        ArrayList<Entry> heartRateEntries = new ArrayList<>();
        ArrayList<Entry> oxygenLevelEntries = new ArrayList<>();
        LineDataSet heartDataSet = new LineDataSet(heartRateEntries, "Heart Rate (bpm)");
        LineDataSet spo2DataSet = new LineDataSet(oxygenLevelEntries, "Oxygen Level (%)");
        heartChart = findViewById(R.id.heartGraph);  // Heart Rate Chart
        spo2Chart = findViewById(R.id.spo2Graph);    // Oxygen Level Chart

        heartText = findViewById(R.id.heartRateTextView);
        spo2Text = findViewById(R.id.OxiTextView);

        // Configure the charts with maximum Y values
        configureChart(heartChart, 200f);  // Assuming the max heart rate is 200 bpm
        configureChart(spo2Chart, 100f);   // Assuming oxygen level max is 100%

        // Loop through the patient data to extract heart rate and oxygen levels
        for (int i = 0; i < patientDataList.size(); i++) {
            PatientData data = patientDataList.get(i);

            // Create heart rate and oxygen level entries
            Entry heartRateEntry = new Entry(i, data.getHeartRate());
            Entry oxygenLevelEntry = new Entry(i, data.getOxygenLevel());

            // BPM Irregularities
            if (data.getHeartRate() > 160) {
                Toast.makeText(DoctorHomeScreen.this, "Critical: High Heart Rate detected!", Toast.LENGTH_SHORT).show();
                Log.e("HealthWarning", "Critical BPM: " + data.getHeartRate());
                heartText.setTextColor(getColor(R.color.emergency));
                heartDataSet.setColor(getColor(R.color.emergency));
                heartDataSet.setCircleColor(getColor(R.color.emergency));
                // Make Phone Call
            }
            if (data.getHeartRate() < 50) {
                Toast.makeText(DoctorHomeScreen.this, "Critical: Slow Heart Rate detected!", Toast.LENGTH_SHORT).show();
                Log.e("HealthWarning", "Critical BPM: " + data.getHeartRate());
                heartText.setTextColor(getColor(R.color.emergency));
                heartDataSet.setColor(getColor(R.color.emergency));
                heartDataSet.setCircleColor(getColor(R.color.emergency));
                // Make Phone Call
            } else {
                heartDataSet.setColor(getColor(R.color.healthy));
                heartDataSet.setCircleColor(getColor(R.color.healthy));
                heartText.setTextColor(getColor(R.color.healthy));  // Reset to default
            }

            // O2 irregularities
            if (data.getOxygenLevel() < 90) {
                Toast.makeText(DoctorHomeScreen.this, "Critical: Major low SPO2 detected!", Toast.LENGTH_SHORT).show();
                Log.e("HealthWarning", "Critical SpO2: " + data.getOxygenLevel());
                spo2Text.setTextColor(getColor(R.color.emergency));
                spo2DataSet.setColor(getColor(R.color.emergency));
                spo2DataSet.setCircleColor(getColor(R.color.emergency));
                // Make Phone Call
            } else if (data.getOxygenLevel() <= 94) {
                Toast.makeText(DoctorHomeScreen.this, "Warning: Mildly low SPO2 detected!", Toast.LENGTH_SHORT).show();
                Log.w("HealthWarning", "Mildly low SpO2: " + data.getOxygenLevel());
                spo2DataSet.setColor(getColor(R.color.mild));
                spo2DataSet.setCircleColor(getColor(R.color.mild));
                spo2Text.setTextColor(getColor(R.color.mild));
            } else {
                spo2DataSet.setColor(getColor(R.color.healthy));
                spo2DataSet.setCircleColor(getColor(R.color.healthy));
                spo2Text.setTextColor(getColor(R.color.healthy));  // Reset to default
            }

            // Add new data points to the datasets and update the charts
            updateChart(heartChart, heartDataSet, data.getHeartRate(), i);
            updateChart(spo2Chart, spo2DataSet, data.getOxygenLevel(), i);
        }

        float lastHeartRate = heartDataSet.getEntryForIndex(heartDataSet.getEntryCount() - 1).getY();
        float lastOxygen = spo2DataSet.getEntryForIndex(spo2DataSet.getEntryCount() - 1).getY();

        heartText.setText(String.format("BPM: %.1f bpm", lastHeartRate));
        spo2Text.setText(String.format("O2: %.1f%%", lastOxygen));
        // Optionally, refresh and notify data change
        heartChart.invalidate();
        spo2Chart.invalidate();
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
