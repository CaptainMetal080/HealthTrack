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

import com.example.healthtrack.R;

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

        //User testUser = new User(0, "John", "Doe", "1234", 647888134, "test@example.com", "patient");
        // Add the user to the database
        User testDR = new User(0, "Liam", "Brown", "1234", 647888134, "dr@example.com", "doctor");
        //DBHelper.addUser(testDR);
    }

    // sign in to account
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
            String userType = DBHelper.getUserTypeByEmail(email);

            if (userType != null) {
                Intent intent = new Intent(MainActivity.this, WelcomeScreen.class);
                intent.putExtra("userType", userType);
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Error retrieving user type!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(MainActivity.this, "Invalid email or password!", Toast.LENGTH_SHORT).show();
        }
    }

    // create new account
    public void navCreateAccount(View v) {
        Intent intent = new Intent(MainActivity.this, NewAccount.class);
        startActivity(intent);
    }
}