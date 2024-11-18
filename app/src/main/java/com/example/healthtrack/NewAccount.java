package com.example.healthtrack;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Patterns;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.healthtrack.R;

public class NewAccount extends AppCompatActivity {

    public DBHelper DBHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        // set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // enable the back button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
        }

        DBHelper = new DBHelper(this);
    }

    public void createNewUser(View v) {
        EditText firstNameInput = findViewById(R.id.create_firstName);
        EditText lastNameInput = findViewById(R.id.create_surname);
        EditText passwordInput = findViewById(R.id.create_password);
        EditText emailInput = findViewById(R.id.create_emailAddress);
        EditText phoneInput = findViewById(R.id.create_phone);
        Spinner userTypeInput = findViewById(R.id.create_type);

        String firstName = firstNameInput.getText().toString();
        String lastName = lastNameInput.getText().toString();
        String password = passwordInput.getText().toString();
        String email = emailInput.getText().toString();
        String phone = phoneInput.getText().toString();
        String selectedUserType = userTypeInput.getSelectedItem().toString();

        if (firstName.isEmpty() || lastName.isEmpty() || password.isEmpty() || email.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_LONG).show();
            return;
        }

        // Validate email format
        if (!isValidEmail(email)) {
            Toast.makeText(this, "Invalid email address", Toast.LENGTH_LONG).show();
            return;
        }

        // Validate phone number format
        if (!isValidPhoneNumber(phone)) {
            Toast.makeText(this, "Invalid phone number", Toast.LENGTH_LONG).show();
            return;
        }

        // Check if email already exists in the database
        if (DBHelper.emailExists(email)) {
            Toast.makeText(this, "Email already exists", Toast.LENGTH_LONG).show();
            return;
        }
        
        User newUser = new User(0, firstName, lastName, password, Long.parseLong(phone), email, selectedUserType);
        DBHelper.addUser(newUser);

    }

    // Helper method to validate email format
    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPhoneNumber(String phone) {
        // Example: Validates if the phone number contains only digits and is 10 digits long
        Pattern pattern = Pattern.compile("^[0-9]{10}$");
        Matcher matcher = pattern.matcher(phone);
        return matcher.matches();
    }
}
