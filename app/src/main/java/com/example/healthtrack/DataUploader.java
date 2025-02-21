package com.example.healthtrack;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class DataUploader {
    private FirebaseFirestore firestore;

    public DataUploader(Context context) {
        firestore = FirebaseFirestore.getInstance();
    }

    public void uploadPatientData(String uid, PatientData patient) {
        Map<String, Object> healthRecord = new HashMap<>();
        healthRecord.put("heartRate", patient.getHeartRate());
        healthRecord.put("oxygenLevel", patient.getOxygenLevel());

        firestore.collection("patient_collection").document(uid)
                .collection("health_records").document(patient.getTime())
                .set(healthRecord)
                .addOnSuccessListener(aVoid -> Log.d("Firestore", "Health record added"))
                .addOnFailureListener(e -> Log.w("Firestore", "Error adding health record", e));
    }
}