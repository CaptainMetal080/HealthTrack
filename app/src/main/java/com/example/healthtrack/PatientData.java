package com.example.healthtrack;

import com.google.gson.annotations.SerializedName;

public class PatientData {
    private String datetime_captured;
    private int heart_rate;
    private int spo2_level;
    private float temperature;
    private int stress_level;

    // Constructor
    public PatientData(String datetime_captured, int heart_rate, int spo2_level, float temperature, int stress_level) {
        this.datetime_captured = datetime_captured;
        this.heart_rate = heart_rate;
        this.spo2_level = spo2_level;
        this.temperature = temperature;
        this.stress_level = stress_level;
    }

    public String getDatetime_captured() {
        return datetime_captured;
    }

    // Getters and setters
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

    @SerializedName("temperature")
    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    @SerializedName("stressLevel")
    public int getStressLevel() {
        return stress_level;
    }

    public void setStressLevel(int stress_level) {
        this.stress_level = stress_level;
    }
}