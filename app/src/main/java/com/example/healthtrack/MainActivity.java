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

import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {



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



//        FirebaseApp.initializeApp(this);
//        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
//        Date dob;
//        try {
//             dob = sdf.parse("January 19, 1970");
//        } catch (ParseException e) {
//            throw new RuntimeException(e);
//        }
//        List<String> patient_list = new ArrayList<>();
//        patient_list.add("Ugdp4eTJqogQNLmkLEpbKEAr7Kr1");
//        patient_list.add("4Zb9phGcAbhZzWldh75sUwF1Rwg1");
//
//        Map<String, Object> patient = new HashMap<>();
//        patient.put("first_name","Victor" );
//        patient.put("last_name","Von" );
//        patient.put("address","582 Guelph St, Kitchener, ON");
//        patient.put("date_of_birth",dob);
//        patient.put("patient_list",patient_list);
//        patient.put("phone", "4326419009");
//        patient.put("specialty", "Geriatrician");
//
//       FirebaseFirestore db_fb = FirebaseFirestore.getInstance();
//       db_fb.collection("doctor_collection").document("JiUJ6GWvchNUt1rwvYSN0YpLuc63")
//               .set(patient)
//               .addOnSuccessListener(aVoid -> Log.d("Firestore", "Patient added"))
//               .addOnFailureListener(e -> Log.w("Firestore", "Error adding patient", e));
//
//        String patientId = "3";
//        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
//
//        Map<String, Object> healthRecord = new HashMap<>();
//        healthRecord.put("heartRate", 75);
//        healthRecord.put("oxygenLevel", 98);
//
//        db_fb.collection("Patient_collections").document(patientId)
//                .collection("health_records").document(timestamp)
//                .set(healthRecord)
//                .addOnSuccessListener(aVoid -> Log.d("Firestore", "Health record added"))
//                .addOnFailureListener(e -> Log.w("Firestore", "Error adding health record", e));

    }
}
