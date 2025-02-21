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
    private FirebaseFirestore firestore;


    public DataUploader(Context context) {
        firestore = FirebaseFirestore.getInstance();
    }


    public void uploadPatientData(PatientData patient) {
        FirebaseFirestore db_fb = FirebaseFirestore.getInstance();

        Map<String, Object> healthRecord = new HashMap<>();
        healthRecord.put("heartRate", patient.getHeartRate());
        healthRecord.put("oxygenLevel", patient.getOxygenLevel());

        db_fb.collection("patient_collections").document(patient.getpId())//Must be changed
                .collection("health_records").document(patient.getTime())
                .set(healthRecord)
                .addOnSuccessListener(aVoid -> Log.d("Firestore", "Health record added"))
                .addOnFailureListener(e -> Log.w("Firestore", "Error adding health record", e));
    }
}

