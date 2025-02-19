package com.example.healthtrack;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private DBHelper DBHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        DBHelper = new DBHelper(this);

        User testUser = new User(0, "John", "Doe", "1234", 647888134, "test@example.com", "patient");
        // Add the user to the database
        User testDR = new User(0, "Liam", "Brown", "1234", 647888134, "dr@example.com", "doctor");
        // DBHelper.addUser(testDR);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> patient = new HashMap<>();
        patient.put("name", "John Doe");
        patient.put("age", 45);
        patient.put("assigned_doctor_id", "doc123");

        FirebaseFirestore db_fb = FirebaseFirestore.getInstance();
        db_fb.collection("Patient_collections").document("2")
                .set(patient)
                .addOnSuccessListener(aVoid -> Log.d("Firestore", "Patient added"))
                .addOnFailureListener(e -> Log.w("Firestore", "Error adding patient", e));

    }

    // Sign in to account
    public void signIn(View v) {
        EditText emailInput = findViewById(R.id.username);
        EditText passwordInput = findViewById(R.id.password);
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please enter both email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        if (DBHelper.checkUserCredentials(email, password)) {
            Intent intent = new Intent(MainActivity.this, WelcomeScreen.class);
            intent.putExtra("email", email); // Pass the email to the WelcomeScreen
            startActivity(intent);
            finish(); // Optional: Close the current activity to prevent back navigation
        } else {
            Toast.makeText(MainActivity.this, "Invalid email or password!", Toast.LENGTH_SHORT).show();
        }
    }

    // Navigate to create new account screen
    public void navCreateAccount(View v) {
        Intent intent = new Intent(MainActivity.this, NewAccount.class);
        startActivity(intent);
    }
}
