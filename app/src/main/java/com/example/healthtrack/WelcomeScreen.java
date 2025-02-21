package com.example.healthtrack;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class WelcomeScreen extends AppCompatActivity {

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome_screen);

        db = FirebaseFirestore.getInstance();

        // Get the UID from the intent
        String uid = getIntent().getStringExtra("uid");

        if (uid != null) {
            // Check if the user is a patient or doctor
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkUserRole(uid);
                }
            }, 3000); // Wait for 3 seconds before redirecting
        }
    }

    private void checkUserRole(String uid) {
        // Check if the user is in the patient_collection
        db.collection("patient_collection").document(uid).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            // User is a patient, redirect to PatientHealthData
                            Intent intent = new Intent(WelcomeScreen.this, PatientHealthData.class);
                            intent.putExtra("uid", uid); // Pass UID
                            startActivity(intent);
                            finish();
                        } else {
                            // Check if the user is in the doctor_collection
                            db.collection("doctor_collection").document(uid).get()
                                    .addOnCompleteListener(task1 -> {
                                        if (task1.isSuccessful()) {
                                            DocumentSnapshot doc = task1.getResult();
                                            if (doc.exists()) {
                                                // User is a doctor, leave function blank for further edits
                                                // You can add logic here later
                                            } else {
                                                // User not found in either collection
                                                Log.w("WelcomeScreen", "User not found in any collection");
                                            }
                                        }
                                    });
                        }
                    }
                });
    }
}