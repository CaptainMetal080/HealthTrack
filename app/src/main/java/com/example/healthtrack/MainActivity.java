package com.example.healthtrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
