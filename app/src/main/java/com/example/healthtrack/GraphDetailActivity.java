package com.example.healthtrack;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GraphDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String patientId;
    private String graphType;
    private LineChart detailedChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph_detail);

        db = FirebaseFirestore.getInstance();
        patientId = getIntent().getStringExtra("patientId");
        graphType = getIntent().getStringExtra("graphType");
        detailedChart = findViewById(R.id.detailedChart);

        fetchGraphData();
    }

    private void fetchGraphData() {
        db.collection("patient_collection").document(patientId)
                .collection("health_records")
                .get() // Remove the orderBy clause to get documents in ascending order by default
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<Entry> entries = new ArrayList<>();
                        List<String> labels = new ArrayList<>();

                        int index = 0;
                        for (DocumentSnapshot document : task.getResult()) {
                            Long value = null;
                            switch (graphType) {
                                case "heartRate":
                                    value = document.getLong("heartRate");
                                    break;
                                case "oxygenLevel":
                                    value = document.getLong("oxygenLevel");
                                    break;
                                case "temperature":
                                    Double temp = document.getDouble("temperature");
                                    if (temp != null) value = temp.longValue();
                                    break;
                            }

                            if (value != null) {
                                entries.add(new Entry(index, value));

                                // Convert Firestore document ID (timestamp) to a short date format
                                String timestamp = document.getId();
                                try {
                                    long timeInMillis = Long.parseLong(timestamp);
                                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault());
                                    String formattedDate = sdf.format(new Date(timeInMillis));
                                    labels.add(formattedDate);
                                } catch (NumberFormatException e) {
                                    Log.e("GraphDetailActivity", "Invalid timestamp format: " + timestamp);
                                    labels.add(timestamp); // Fallback to raw timestamp if parsing fails
                                }

                                index++;
                            }
                        }

                        // Update the chart with the fetched data
                        updateChart(entries, labels);
                    } else {
                        Log.e("GraphDetailActivity", "Error fetching graph data", task.getException());
                    }
                });
    }

    private void updateChart(List<Entry> entries, List<String> labels) {
        LineDataSet dataSet = new LineDataSet(entries, graphType);
        dataSet.setColor(getColor(R.color.healthy));
        dataSet.setCircleColor(getColor(R.color.healthy));
        dataSet.setLineWidth(2f);
        dataSet.setCircleSize(4f);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        detailedChart.setData(lineData);

        // Configure the chart
        detailedChart.getDescription().setEnabled(false);
        detailedChart.setDragEnabled(true);
        detailedChart.setScaleEnabled(true);
        detailedChart.setPinchZoom(true);

        // Customize X-axis
        XAxis xAxis = detailedChart.getXAxis();
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(labels.size());
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setTextSize(6f);
        xAxis.setLabelRotationAngle(-45); // Rotate labels for better readability

        // Customize Y-axis
        YAxis yAxis = detailedChart.getAxisLeft();
        yAxis.setGranularity(1f);
        yAxis.setDrawGridLines(true);
        yAxis.setTextSize(10f);

        // Disable right Y-axis
        detailedChart.getAxisRight().setEnabled(false);

        // Add padding to the chart
        detailedChart.setExtraOffsets(20f, 20f, 20f, 20f);

        // Refresh the chart
        detailedChart.invalidate();
    }
}