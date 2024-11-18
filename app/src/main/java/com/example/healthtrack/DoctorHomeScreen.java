package com.example.healthtrack;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DoctorHomeScreen extends AppCompatActivity {
    private ApiService apiService;
    private List<PatientData> patientDataList;

    private Handler handler;
    private final int FETCH_INTERVAL = 15000; // 15 seconds

    private LineChart heartChart;
    private LineChart spo2Chart;

    private int MAX_POINTS=40;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_screen);

        patientDataList = new ArrayList<>();
        handler = new Handler();
        apiService = RetrofitClient.getClient().create(ApiService.class);

        startFetchingData();
    }

    private void startFetchingData() {
        handler.post(fetchDataRunnable);
    }

    private final Runnable fetchDataRunnable = new Runnable() {
        @Override
        public void run() {
            fetchHealthData(1);

            // Schedule the next fetch after the interval
            handler.postDelayed(this, FETCH_INTERVAL);
        }
    };

    public void fetchHealthData(Integer patientId) {
        // Fetch data with or without a patientId filter
        Call<List<PatientData>> call = apiService.getHealthData(patientId);
        call.enqueue(new Callback<List<PatientData>>() {
            @Override
            public void onResponse(Call<List<PatientData>> call, Response<List<PatientData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    patientDataList = response.body();
                    for (PatientData record : patientDataList) {
                        Log.d("DataFetcher", "Fetched record: " + record.getHeartRate() + ", " + record.getOxygenLevel());
                    }

                    plotHealthData();
                } else {
                    Log.e("DataFetcher", "Failed to fetch data: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<List<PatientData>> call, Throwable t) {
                Log.e("DataFetcher", "Error: " + t.getMessage());
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

   private void plotHealthData() {
       // Prepare data for the charts (e.g., heart rate or oxygen levels)
       ArrayList<Entry> heartRateEntries = new ArrayList<>();
       ArrayList<Entry> oxygenLevelEntries = new ArrayList<>();

       heartChart = findViewById(R.id.heartGraph);  // Heart Rate Chart
       spo2Chart = findViewById(R.id.spo2Graph);    // Oxygen Level Chart

       // Configure the charts with maximum Y values
       configureChart(heartChart, 200f);  // Assuming the max heart rate is 200 bpm
       configureChart(spo2Chart, 100f);   // Assuming oxygen level max is 100%

       // Loop through the patient data to extract heart rate and oxygen levels
       for (int i = 0; i < patientDataList.size(); i++) {
           PatientData data = patientDataList.get(i);

           // Create heart rate and oxygen level entries
           Entry heartRateEntry = new Entry(i, data.getHeartRate());
           Entry oxygenLevelEntry = new Entry(i, data.getOxygenLevel());

           // Add new data points to the datasets and update the charts
           updateChart(heartChart, new LineDataSet(heartRateEntries, "Heart Rate (bpm)"), data.getHeartRate(), i);
           updateChart(spo2Chart, new LineDataSet(oxygenLevelEntries, "Oxygen Level (%)"), data.getOxygenLevel(), i);
       }

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
