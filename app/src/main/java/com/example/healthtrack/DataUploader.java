package com.example.healthtrack;
import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.firebase.firestore.FirebaseFirestore;

public class DataUploader {
    private DBHelper dbHelper;
    private FirebaseFirestore firestore;

    public DataUploader(Context context) {
        dbHelper = new DBHelper(context);
        firestore = FirebaseFirestore.getInstance();
    }

    public void uploadPatientDataBatch() {
        // Get the last 10 records from the database
        List<PatientData> patientDataList = dbHelper.getLast10PatientData();

        if (!patientDataList.isEmpty()) {
            // Upload each record to Firebase Firestore

            for (PatientData patientData : patientDataList) {
                // Create a new document for each patient data record
                firestore.collection("patient_collections").add(patientData)
                        .addOnSuccessListener(documentReference -> {
                            // If successful, remove these records from the local database
                            dbHelper.deletePatientData(patientData.getpId());
                        })
                        .addOnFailureListener(e -> {
                            Log.e("Upload", "Failed to upload data: " + e.getMessage());
                        });
            }
        }
    }
}

