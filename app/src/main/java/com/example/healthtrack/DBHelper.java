package com.example.healthtrack;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "HealthTrack.db";
    private static final int DATABASE_VERSION = 1;

    // User table columns
    public static final String USERS_TABLE_NAME = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FIRST_NAME = "first_name";
    public static final String COLUMN_LAST_NAME = "last_name";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PHONE = "phone";
    public static final String COLUMN_TYPE = "user_type";

    // SQL statement to create the user table
    public static final String CREATE_USER_TABLE = "CREATE TABLE " + USERS_TABLE_NAME + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_FIRST_NAME + " TEXT, " +
            COLUMN_LAST_NAME + " TEXT, " +
            COLUMN_PASSWORD + " TEXT, " +
            COLUMN_EMAIL + " TEXT UNIQUE, " +
            COLUMN_PHONE + " REAL, " +
            COLUMN_TYPE + " TEXT); ";

    public DBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USER_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + USERS_TABLE_NAME);
        onCreate(db);
    }

    // Add a new user to the database
    public boolean addUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FIRST_NAME, user.getFirstName());
        values.put(COLUMN_LAST_NAME, user.getLastName());
        values.put(COLUMN_PASSWORD, user.getPassword());
        values.put(COLUMN_EMAIL, user.getEmail());
        values.put(COLUMN_PHONE, user.getPhone());
        values.put(COLUMN_TYPE, user.getUserType());

        // Insert user into the database
        long result = db.insert(USERS_TABLE_NAME, null, values);
        db.close();
        return result != -1; // Return true if the insertion is successful
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
}
