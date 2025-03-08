package com.example.healthtrack;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class WelcomeScreen extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private TextView welcomeText;
    private TextView nameText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome_screen);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize TextViews
        welcomeText = findViewById(R.id.textView2);
        nameText = findViewById(R.id.nameTextView);

        // Update welcome text immediately
        updateWelcomeText();

        // Wait for 3 seconds before checking the user role and redirecting
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkUserRole();
            }
        }, 3000); // Wait for 3 seconds before redirecting
    }

    private void updateWelcomeText() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String uid = user.getUid();

            // Check if the user is in the patient_collection
            db.collection("patient_collection").document(uid).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                // User is a patient, fetch name and update welcome text
                                String firstName = document.getString("first_name");
                                String lastName = document.getString("last_name");
                                welcomeText.setText("Welcome!");
                                nameText.setText(firstName + " " + lastName);
                            } else {
                                // Check if the user is in the doctor_collection
                                db.collection("doctor_collection").document(uid).get()
                                        .addOnCompleteListener(task1 -> {
                                            if (task1.isSuccessful()) {
                                                DocumentSnapshot doc = task1.getResult();
                                                if (doc.exists()) {
                                                    // User is a doctor, fetch name and update welcome text
                                                    String lastName = doc.getString("last_name");
                                                    welcomeText.setText("Welcome!");
                                                    nameText.setText("DR. " + lastName);
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
                                                    // User is a doctor, redirect to DoctorHomePage
                                                    Intent intent = new Intent(WelcomeScreen.this, DoctorHomePage.class);
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