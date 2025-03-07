package com.example.healthtrack;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class WelcomeScreen extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome_screen);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Check if the user is a patient or doctor
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkUserRole();
            }
        }, 3000); // Wait for 3 seconds before redirecting
    }

    private void checkUserRole() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String uid = user.getUid();

            // Check if the user is in the patient_collection
            db.collection("patient_collection").document(uid).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                // User is a patient, redirect to PatientHealthData_nosensor
                                Intent intent = new Intent(WelcomeScreen.this, PatientHealthData_nosensor.class);
                                startActivity(intent);
                                finish();
                            } else {
                                // Check if the user is in the doctor_collection
                                db.collection("doctor_collection").document(uid).get()
                                        .addOnCompleteListener(task1 -> {
                                            if (task1.isSuccessful()) {
                                                DocumentSnapshot doc = task1.getResult();
                                                if (doc.exists()) {
                                                    // User is a doctor, redirect to DoctorHomeScreen
                                                    Intent intent = new Intent(WelcomeScreen.this, DoctorHomePage_WIP.class);
                                                    startActivity(intent);
                                                    finish();
                                                } else {
                                                    // User not found in either collection
                                                    Log.w("WelcomeScreen", "User not found in any collection");
                                                }
                                            }
                                        });
                            }
                        }
                    });
        } else {
            Log.w("WelcomeScreen", "No authenticated user found");
        }
    }
}