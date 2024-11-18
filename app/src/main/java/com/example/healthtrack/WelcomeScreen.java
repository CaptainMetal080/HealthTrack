package com.example.healthtrack;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

public class WelcomeScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome_screen);

        // Get the email from the intent
        String email = getIntent().getStringExtra("email");

        if (email != null) {
            // Get the user type from the DB
            DBHelper dbHelper = new DBHelper(this);
            String userType = dbHelper.getUserTypeByEmail(email);
            int id = dbHelper.getUserIdByEmail(email);

            // Redirect to the appropriate screen based on user type
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent;
                    if (userType.equals("doctor")) {
                        // Get the last name of the doctor
                        String lastName = dbHelper.getLastNameByEmail(email);

                        // Pass the last name to DoctorHomeScreen
                        intent = new Intent(WelcomeScreen.this, DoctorHomeScreen.class);
                        intent.putExtra("lastName", lastName);  // Pass the last name
                    } else if (userType.equals("patient")) {
                        intent = new Intent(WelcomeScreen.this, PatientHealthData.class);
                    } else {
                        // Default to Patient Health Data if the type is unknown
                        intent = new Intent(WelcomeScreen.this, PatientHealthData.class);
                    }

                    intent.putExtra("id", String.valueOf(id));
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                }
            }, 3000); // Wait for 3 seconds before redirecting
        }
    }
}
