package com.example.healthtrack;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GraphDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String patientId;
    private String graphType;
    private LineChart detailedChart;
    private TextView patientNameView;
    private TextView patientAgeView;
    private Button callPatientButton;
    private Button callEmergencyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph_detail);

        db = FirebaseFirestore.getInstance();
        patientId = getIntent().getStringExtra("patientId");
        graphType = getIntent().getStringExtra("graphType");
        detailedChart = findViewById(R.id.detailedChart);
        patientNameView = findViewById(R.id.patientName);
        patientAgeView = findViewById(R.id.patientAge);
        callPatientButton = findViewById(R.id.callPatientButton);
        callEmergencyButton = findViewById(R.id.callEmergencyButton);

        // Fetch and display patient data
        fetchPatientData();

        // Fetch and plot graph data
        fetchGraphData();

        // Set up button click listeners
        callPatientButton.setOnClickListener(v -> callPatient());
        callEmergencyButton.setOnClickListener(v -> callEmergency());
    }

    private void fetchPatientData() {
        db.collection("patient_collection").document(patientId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        // Fetch patient details
                        String firstName = document.getString("first_name");
                        String lastName = document.getString("last_name");
                        String phoneNumber = document.getString("phone");

                        // Display patient name
                        patientNameView.setText("Name: " + firstName + " " + lastName);

                        // Fetch and calculate age from date_of_birth
                        Object dateOfBirthObj = document.get("date_of_birth");
                        if (dateOfBirthObj != null) {
                            String dateOfBirth;


                            com.google.firebase.Timestamp timestamp = (com.google.firebase.Timestamp) dateOfBirthObj;
                            Date birthDate = timestamp.toDate();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            dateOfBirth = sdf.format(birthDate);

                            int age = calculateAge(dateOfBirth);
                            patientAgeView.setText("Age: " + age);

                        }

                        // Store phone number for calling
                        callPatientButton.setTag(phoneNumber); // Store phone number in the button's tag
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("GraphDetailActivity", "Error fetching patient data", e);
                });
    }

    private int calculateAge(String dateOfBirth) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date birthDate = sdf.parse(dateOfBirth);
            if (birthDate != null) {
                Calendar today = Calendar.getInstance();
                Calendar dob = Calendar.getInstance();
                dob.setTime(birthDate);

                int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
                if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                    age--;
                }
                return age;
            }
        } catch (Exception e) {
            Log.e("GraphDetailActivity", "Error calculating age", e);
        }
        return 0; // Default age if calculation fails
    }

    private void callPatient() {
        String phoneNumber = (String) callPatientButton.getTag();
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phoneNumber));
            startActivity(intent);
        } else {
            Log.e("GraphDetailActivity", "No phone number found for the patient");
        }
    }

    private void callEmergency() {
        // No implementation needed for calling 911
        Log.d("GraphDetailActivity", "Call 911 button clicked");
    }

    // Rest of the code (fetchGraphData, updateChart, etc.) remains unchanged
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
        if (entries.isEmpty()) {
            Log.w("ChartUpdate", "No data to plot for " + graphType);
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, graphType);

        // Configure colors for each point based on its value
        List<Integer> colors = new ArrayList<>();
        for (Entry entry : entries) {
            float value = entry.getY();
            switch (graphType) {
                case "heartRate":
                    if (value > 160 || value < 50) {
                        colors.add(getColor(R.color.emergency));
                    } else {
                        colors.add(getColor(R.color.healthy));
                    }
                    break;
                case "oxygenLevel":
                    if (value < 90) {
                        colors.add(getColor(R.color.emergency));
                    } else if (value <= 94) {
                        colors.add(getColor(R.color.mild));
                    } else {
                        colors.add(getColor(R.color.healthy));
                    }
                    break;
                case "temperature":
                    if (value > 40 || value < 35) {
                        colors.add(getColor(R.color.emergency));
                    } else {
                        colors.add(getColor(R.color.healthy));
                    }
                    break;
            }
        }
        dataSet.setColor(getColor(R.color.baseline));
        // Set the colors for each point
        dataSet.setCircleColors(colors);
        dataSet.setLineWidth(2f);
        dataSet.setCircleSize(3f);
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
        xAxis.setTextSize(6f); // Adjust text size if needed
        xAxis.setLabelRotationAngle(-45); // Rotate labels for better readability

        // Add extra space at the bottom for X-axis labels
        xAxis.setYOffset(10f); // Move X-axis labels further down
        xAxis.setAvoidFirstLastClipping(true); // Prevent clipping of first and last labels

        // Customize Y-axis
        YAxis yAxis = detailedChart.getAxisLeft();
        yAxis.setGranularity(1f);
        yAxis.setDrawGridLines(true);
        yAxis.setTextSize(10f);

        // Disable right Y-axis
        detailedChart.getAxisRight().setEnabled(false);

        // Add padding to the chart (extra bottom offset for X-axis labels)
        detailedChart.setExtraOffsets(20f, 20f, 20f, 40f); // Increase bottom offset to 40f

        // Refresh the chart
        detailedChart.invalidate();
    }
}