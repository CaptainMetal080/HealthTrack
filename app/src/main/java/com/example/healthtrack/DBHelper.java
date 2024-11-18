package com.example.healthtrack;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "HealthTrack.db";
    private static final int DATABASE_VERSION = 2;  // Incremented version for schema change

    // User table columns
    public static final String USERS_TABLE_NAME = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FIRST_NAME = "first_name";
    public static final String COLUMN_LAST_NAME = "last_name";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PHONE = "phone";
    public static final String COLUMN_TYPE = "user_type";

    // PatientData table columns
    public static final String PATIENT_TABLE_NAME = "patient_data";
    public static final String COLUMN_PATIENTID_TIME = "patientId_time";
    public static final String COLUMN_PATIENT_ID = "p_id";
    public static final String COLUMN_TIME = "time"; // Replacing email with time
    public static final String COLUMN_HEART_RATE = "heart_rate";
    public static final String COLUMN_OXYGEN_LEVEL = "oxygen_level";

    // SQL statement to create the user table
    public static final String CREATE_USER_TABLE = "CREATE TABLE " + USERS_TABLE_NAME + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_FIRST_NAME + " TEXT, " +
            COLUMN_LAST_NAME + " TEXT, " +
            COLUMN_PASSWORD + " TEXT, " +
            COLUMN_EMAIL + " TEXT UNIQUE, " +
            COLUMN_PHONE + " REAL, " +
            COLUMN_TYPE + " TEXT);";

    // SQL statement to create the patient data table
    public static final String CREATE_PATIENT_TABLE = "CREATE TABLE " + PATIENT_TABLE_NAME + " (" +
    COLUMN_PATIENT_ID + " INTEGER, " +
    COLUMN_TIME + " TEXT, " +
    COLUMN_HEART_RATE + " INTEGER, " +
    COLUMN_OXYGEN_LEVEL + " INTEGER, " +
    "PRIMARY KEY (" + COLUMN_PATIENT_ID + ", " + COLUMN_TIME + "));";

    public DBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            // Check if the patient_data table exists before creating it
            Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + PATIENT_TABLE_NAME + "'", null);
            if (cursor != null && cursor.getCount() == 0) {
                // Create the patient data table if it doesn't exist
                db.execSQL(CREATE_PATIENT_TABLE);
            }
            cursor.close();

            // Create user table
            db.execSQL(CREATE_USER_TABLE);

        } catch (Exception e) {
            Log.e("DBHelper", "Error creating tables", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            if (oldVersion < 2) {
                // Drop old tables if they exist and recreate
                db.execSQL("DROP TABLE IF EXISTS " + USERS_TABLE_NAME);
                db.execSQL("DROP TABLE IF EXISTS " + PATIENT_TABLE_NAME);
                onCreate(db);
            }
        } catch (Exception e) {
            Log.e("DBHelper", "Error upgrading database", e);
        }
    }

    // Add a new patient record
    public void addPatientData(PatientData patientData) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PATIENT_ID, patientData.getpId());
        values.put(COLUMN_TIME, patientData.getTime()); // Use time instead of email
        values.put(COLUMN_HEART_RATE, patientData.getHeartRate());
        values.put(COLUMN_OXYGEN_LEVEL, patientData.getOxygenLevel());

        db.insert(PATIENT_TABLE_NAME, null, values);
        db.close();
    }

    // Retrieve patient data by ID
    @SuppressLint("Range")
    public PatientData getPatientDataById(int pId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(PATIENT_TABLE_NAME, null, COLUMN_PATIENT_ID + " = ?",
                new String[]{String.valueOf(pId)}, null, null, null);

        PatientData patientData = null;
        if (cursor != null && cursor.moveToFirst()) {
            int heartRate = cursor.getInt(cursor.getColumnIndex(COLUMN_HEART_RATE));
            int oxygenLevel = cursor.getInt(cursor.getColumnIndex(COLUMN_OXYGEN_LEVEL));
            String time = cursor.getString(cursor.getColumnIndex(COLUMN_TIME)); // Get time instead of email

            patientData = new PatientData(pId, time, heartRate, oxygenLevel);
        }
        cursor.close();
        db.close();
        return patientData;
    }

    // Update patient data by ID
    public boolean updatePatientData(PatientData patientData) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_HEART_RATE, patientData.getHeartRate());
        values.put(COLUMN_OXYGEN_LEVEL, patientData.getOxygenLevel());

        int rowsAffected = db.update(PATIENT_TABLE_NAME, values, COLUMN_PATIENT_ID + " = ?",
                new String[]{String.valueOf(patientData.getpId())});
        db.close();
        return rowsAffected > 0;
    }

    // Delete patient data by ID
    public boolean deletePatientData(int pId) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(PATIENT_TABLE_NAME, COLUMN_PATIENT_ID + " = ?",
                new String[]{String.valueOf(pId)});
        db.close();
        return rowsDeleted > 0;
    }

    // Get all patient data
    public Cursor getAllPatientData() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + PATIENT_TABLE_NAME, null);
    }

    // Get the last 10 patient data records
    public List<PatientData> getLast10PatientData() {
        SQLiteDatabase db = this.getReadableDatabase();
        List<PatientData> patientDataList = new ArrayList<>();

        // Query to fetch the last 10 patient records (ordered by patient ID, adjust if needed)
        Cursor cursor = db.rawQuery("SELECT * FROM " + PATIENT_TABLE_NAME + " ORDER BY " + COLUMN_PATIENT_ID + " DESC LIMIT 10", null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") int pId = cursor.getInt(cursor.getColumnIndex(COLUMN_PATIENT_ID));
                @SuppressLint("Range") int heartRate = cursor.getInt(cursor.getColumnIndex(COLUMN_HEART_RATE));
                @SuppressLint("Range") int oxygenLevel = cursor.getInt(cursor.getColumnIndex(COLUMN_OXYGEN_LEVEL));
                @SuppressLint("Range") String time = cursor.getString(cursor.getColumnIndex(COLUMN_TIME));

                PatientData patientData = new PatientData(pId, time, heartRate, oxygenLevel);
                patientDataList.add(patientData);
            }
            cursor.close();
        }
        db.close();
        return patientDataList;
    }

    public void addUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FIRST_NAME, user.getFirstName());
        values.put(COLUMN_LAST_NAME, user.getLastName());
        values.put(COLUMN_PASSWORD, user.getPassword());
        values.put(COLUMN_EMAIL, user.getEmail());
        values.put(COLUMN_PHONE, user.getPhone());
        values.put(COLUMN_TYPE, user.getUserType());

        // Insert user into the database
        db.insert(USERS_TABLE_NAME, null, values);
        db.close();
    }

    // Check if user credentials match
    public boolean checkUserCredentials(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        boolean userExists = false;

        // Query the user table for a match with the email and password
        String query = "SELECT * FROM " + USERS_TABLE_NAME + " WHERE " + COLUMN_EMAIL + " = ? AND " + COLUMN_PASSWORD + " = ?";
        cursor = db.rawQuery(query, new String[]{email, password});

        if (cursor != null && cursor.moveToFirst()) {
            // If a row is returned, it means user credentials match
            userExists = true;
        }
        cursor.close();
        db.close();
        return userExists;
    }

    // Check if the email exists in the database
    public boolean emailExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + USERS_TABLE_NAME + " WHERE " + COLUMN_EMAIL + " = ?", new String[]{email});

        boolean exists = cursor.moveToFirst();
        cursor.close();
        db.close();
        return exists;
    }

    // Get user type based on email
    @SuppressLint("Range")
    public String getUserTypeByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                USERS_TABLE_NAME, // Table name
                new String[]{COLUMN_TYPE}, // Column to retrieve
                COLUMN_EMAIL + " = ?", // WHERE clause
                new String[]{email}, // Arguments for the WHERE clause
                null, null, null);

        String userType = null;
        if (cursor != null && cursor.moveToFirst()) {
            userType = cursor.getString(cursor.getColumnIndex(COLUMN_TYPE));
        }
        cursor.close();
        db.close();
        return userType; // Return null if user type is not found
    }

    @SuppressLint("Range")
    public int getUserIdByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        int userId = -1; // Default value if user is not found

        // Query to get the user ID based on the email
        String query = "SELECT " + COLUMN_ID + " FROM " + USERS_TABLE_NAME + " WHERE " + COLUMN_EMAIL + " = ?";
        cursor = db.rawQuery(query, new String[]{email});

        if (cursor != null && cursor.moveToFirst()) {
            // Retrieve the user ID from the cursor
            userId = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
        }

        return userId;
    }
    @SuppressLint("Range")
    public String getLastNameByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                USERS_TABLE_NAME,    // Table name
                new String[]{COLUMN_LAST_NAME},  // Column to retrieve
                COLUMN_EMAIL + " = ?",  // WHERE clause
                new String[]{email},    // Arguments for the WHERE clause
                null, null, null);

        String lastName = null;
        if (cursor != null && cursor.moveToFirst()) {
            lastName = cursor.getString(cursor.getColumnIndex(COLUMN_LAST_NAME));
        }
        cursor.close();
        db.close();
        return lastName;  // Return null if last name is not found
    }
}
