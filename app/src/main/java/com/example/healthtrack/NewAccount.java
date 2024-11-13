package com.example.healthtrack;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

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

        User newUser = new User(0, firstName, lastName, password, Long.parseLong(phone), email, selectedUserType);
        boolean userAdded = DBHelper.addUser(newUser);
        if (userAdded) {
            Toast.makeText(this, "Account created!", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "Error creating account, please try again!", Toast.LENGTH_LONG).show();
        }
    }
}
