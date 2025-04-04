package com.example.healthtrack;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.github.mikephil.charting.components.LimitLine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GraphDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String patientId;
    private String graphType;
    private LineChart detailedChart;
    private TextView patientNameView;
    private TextView patientAgeView;
    private Button callButton;
    private Button callEmergencyButton;
    private String currentUserId;
    private boolean isDoctor;
    private HeartRatePredictor predictor;
    private float MaxHRthreshold;
    private float MinHRthreshold;
    private float predictedROC;
    private boolean isAnomalyDetected = false;

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
        callButton = findViewById(R.id.callPatientButton);
        callEmergencyButton = findViewById(R.id.callEmergencyButton);
        currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        // Determine if the current user is a doctor or a patient
        checkUserRole();

        // Fetch and display patient data
        fetchPatientData();

        // Initialize the predictor
        predictor = new HeartRatePredictor(this);
        // Fetch and plot graph data
        fetchGraphData();

        // Set up button click listeners
        callButton.setOnClickListener(v -> callContact());
        callEmergencyButton.setOnClickListener(v -> callEmergency());
    }

    private void checkUserRole() {
        db.collection("doctor_collection").document(currentUserId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        isDoctor = true;
                        callButton.setText("Call Patient");
                    } else {
                        isDoctor = false;
                        callButton.setText("Call Doctor");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("GraphDetailActivity", "Error checking user role", e);
                });
    }

    private void fetchPatientData() {
        db.collection("patient_collection").document(patientId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        // Fetch patient details
                        String firstName = document.getString("first_name");
                        String lastName = document.getString("last_name");
                        String phoneNumber = document.getString("phone");
                        String doctorId = document.getString("doctor_ID");

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

                        // Store phone number or doctor ID for calling
                        if (isDoctor) {
                            callButton.setTag(phoneNumber); // Store patient's phone number
                        } else {
                            callButton.setTag(doctorId); // Store doctor's ID
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("GraphDetailActivity", "Error fetching patient data", e);
                });
    }

    private void callContact() {
        if (isDoctor) {
            String phoneNumber = (String) callButton.getTag();
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + phoneNumber));
                startActivity(intent);
            } else {
                Log.e("GraphDetailActivity", "No phone number found for the patient");
            }
        } else {
            String doctorId = (String) callButton.getTag();
            if (doctorId != null) {
                db.collection("doctor_collection").document(doctorId).get()
                        .addOnSuccessListener(document -> {
                            if (document.exists()) {
                                String doctorPhoneNumber = document.getString("phone");
                                if (doctorPhoneNumber != null && !doctorPhoneNumber.isEmpty()) {
                                    Intent intent = new Intent(Intent.ACTION_DIAL);
                                    intent.setData(Uri.parse("tel:" + doctorPhoneNumber));
                                    startActivity(intent);
                                } else {
                                    Log.e("GraphDetailActivity", "No phone number found for the doctor");
                                }
                            } else {
                                Log.e("GraphDetailActivity", "Doctor document does not exist");
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e("GraphDetailActivity", "Error fetching doctor's phone number", e);
                        });
            } else {
                Log.e("GraphDetailActivity", "No doctor ID found for the patient");
            }
        }
    }

    private void callEmergency() {
        // No implementation needed for calling 911
        Log.d("GraphDetailActivity", "Call 911 button clicked");
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

    private void fetchGraphData() {
        db.collection("patient_collection").document(patientId)
                .collection("health_records")
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING) // Newest first
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e("GraphDetailActivity", "Error fetching graph data", error);
                        return;
                    }

                    if (snapshots != null && !snapshots.isEmpty()) {
                        List<Entry> entries = new ArrayList<>();
                        List<String> labels = new ArrayList<>();
                        List<Float> last10HeartRates = new ArrayList<>();
                        // Iterate through the snapshots in reverse order (oldest first)
                        List<DocumentSnapshot> documents = new ArrayList<>(snapshots.getDocuments());
                        Collections.reverse(documents);

                        for (int i = 0; i < documents.size(); i++) {
                            DocumentSnapshot document = documents.get(i);
                            Long value = null;

                            switch (graphType) {
                                case "heartRate":
                                    value = document.getLong("heartRate");
                                    if (value != null) {
                                        // Maintain last 10 readings for prediction
                                        if (last10HeartRates.size() < 10) {
                                            last10HeartRates.add(value.floatValue());
                                        } else {
                                            last10HeartRates.remove(0);
                                            last10HeartRates.add(value.floatValue());
                                        }
                                    }
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
                                entries.add(new Entry(i, value));

                                // Convert Firestore document ID (timestamp) to a short date format
                                String timestamp = document.getId();
                                try {
                                    long timeInMillis = Long.parseLong(timestamp);
                                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault());
                                    String formattedDate = sdf.format(new Date(timeInMillis));
                                    labels.add(formattedDate);
                                } catch (NumberFormatException e) {
                                    // Log.e("GraphDetailActivity", "Invalid timestamp format: " + timestamp);
                                    labels.add(timestamp); // Fallback to raw timestamp if parsing fails
                                }

                            }
                        }
                        // Predict heart rate threshold if we have 10 readings
                        if (graphType.equals("heartRate") && last10HeartRates.size() == 10) {
                            predictHeartRate(last10HeartRates);
                        }

                        updateChart(entries, labels);
                    } else {
                        Log.w("GraphDetailActivity", "No data found for graph type: " + graphType);
                    }
                });
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
                if (hr > MaxHRthreshold || hr < MinHRthreshold) {
                    anomalyDetected = true;
                    break;
                }
            }

            // Update global anomaly status
            isAnomalyDetected = anomalyDetected;

            // Show results
            if (isAnomalyDetected) {

                Log.w("AnomalyDetection", "Anomaly detected! Last HR: " + lastHR + " Threshold: " + MaxHRthreshold);
            }

            Log.w("Machine Learning Heart Rate", String.format("Predicted ROC: %.2f | Threshold: %.1f BPM", predictedROC, MaxHRthreshold));
        } catch (Exception e) {
                Log.w("Prediction Error", String.format("Unexpected prediction error occured"));
            }
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
                    if (value > MaxHRthreshold||value<MinHRthreshold) {
                        Toast.makeText(this, "⚠️ Heart Rate Anomaly Detected!", Toast.LENGTH_LONG).show();
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

        if (graphType.equals("heartRate")) {
            YAxis leftAxis = detailedChart.getAxisLeft();
            leftAxis.removeAllLimitLines();

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

            leftAxis.addLimitLine(upperLimit);
            leftAxis.addLimitLine(lowerLimit);
        }

        // Disable right Y-axis
        detailedChart.getAxisRight().setEnabled(false);

        // Add padding to the chart (extra bottom offset for X-axis labels)
        detailedChart.setExtraOffsets(20f, 20f, 20f, 40f); // Increase bottom offset to 40f

        // Refresh the chart
        detailedChart.invalidate();
    }
}