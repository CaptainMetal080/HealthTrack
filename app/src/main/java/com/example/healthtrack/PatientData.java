package com.example.healthtrack;

public class PatientData {
    private int pId;
    private long time; // Changed from email to time
    private int heartRate;
    private int oxygenLevel;

    // Updated constructor to use time instead of email
    public PatientData(int pId, long time, int heartRate, int oxygenLevel) {
        this.pId = pId;
        this.time = time;
        this.heartRate = heartRate;
        this.oxygenLevel = oxygenLevel;
    }

    // Getters and setters
    public int getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(int heartRate) {
        this.heartRate = heartRate;
    }

    public int getOxygenLevel() {
        return oxygenLevel;
    }

    public void setOxygenLevel(int oxygenLevel) {
        this.oxygenLevel = oxygenLevel;
    }

    public int getpId() {
        return pId;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
