package com.example.myapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "HealthTrack.db";
    private static final int DATABASE_VERSION = 1;

    // user table
    public static final String USERS_TABLE_NAME = "users";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FIRST_NAME = "first_name";
    public static final String COLUMN_LAST_NAME = "last_name";
    public static final String COLUMN_PASSWORD = "password";
    public static final String COLUMN_EMAIL = "email";
    public static final String COLUMN_PHONE = "phone";
    public static final String COLUMN_TYPE = "user_type";
    public static final String CREATE_USER_TABLE = "CREATE TABLE " + USERS_TABLE_NAME + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_FIRST_NAME + " TEXT, "
            + COLUMN_LAST_NAME + " TEXT, "
            + COLUMN_PASSWORD + " TEXT, "
            + COLUMN_EMAIL + " TEXT, "
            + COLUMN_PHONE + " REAL, "
            + COLUMN_TYPE + " TEXT); ";

    public DBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_USER_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i1) {
        db.execSQL("DROP TABLE IF EXISTS " + USERS_TABLE_NAME);
        onCreate(db);
    }

    // create new user
    public boolean addUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, user.getId());
        values.put(COLUMN_FIRST_NAME, user.getFirstName());
        values.put(COLUMN_LAST_NAME, user.getLastName());
        values.put(COLUMN_PASSWORD, user.getPassword());
        values.put(COLUMN_PHONE, user.getPhone());
        values.put(COLUMN_EMAIL, user.getEmail());
        values.put(COLUMN_TYPE, user.getUserType());
        long var = db.insert(USERS_TABLE_NAME, null, values);
        db.close();
        return var != -1;
    }
}
