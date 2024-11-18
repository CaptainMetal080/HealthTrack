package com.example.healthtrack;

import com.google.gson.annotations.SerializedName;

public class PatientData {
    private int patient_id;
    private String datetime_captured;
    private int heart_rate;
    private int spo2_level;

    // Updated constructor to use time instead of email
    public PatientData(int patient_id, String datetime_captured, int heart_rate, int spo2_level) {
        this.patient_id = patient_id;
        this.datetime_captured = datetime_captured;
        this.heart_rate = heart_rate;
        this.spo2_level = spo2_level;
    }

    // Getters and setters
    @SerializedName("patientId")
    public int getpId() {
        return patient_id;
    }

    @SerializedName("timestamp")
    public String getTime() {
        return datetime_captured;
    }


    public void setTime(String datetime_captured) {
        this.datetime_captured = datetime_captured;
    }

    @SerializedName("heartRate")
    public int getHeartRate() {
        return heart_rate;
    }

    public void setHeartRate(int heart_rate) {
        this.heart_rate = heart_rate;
    }

    @SerializedName("oxygenLevel")
    public int getOxygenLevel() {
        return spo2_level;
    }

    public void setOxygenLevel(int spo2_level) {
        this.spo2_level = spo2_level;
    }
}
