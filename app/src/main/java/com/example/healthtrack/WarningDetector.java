package com.example.healthtrack;

import java.util.ArrayList;
import java.util.List;

public class WarningDetector {
    private static final int WINDOW_SIZE = 5; // Analyze last 5 readings

    public static List<String> detectWarnings(List<PatientData> patientData) {
        List<String> warnings = new ArrayList<>();

        if (patientData.size() < WINDOW_SIZE) {
            return warnings; // Not enough data to analyze
        }

        // Get the last 5 readings
        List<PatientData> lastReadings = patientData.subList(patientData.size() - WINDOW_SIZE, patientData.size());

        // Check for Sleep Apnea Risk
        if (isSleepApneaRisk(lastReadings)) {
            warnings.add("Possible sleep apnea risk detected.");
        }

        // Check for Early Hypoxia Warning
        if (isEarlyHypoxiaWarning(lastReadings)) {
            warnings.add("Possible oxygen deprivation detected.");
        }

        return warnings;
    }

    private static boolean isSleepApneaRisk(List<PatientData> readings) {
        int spO2Drops = 0;
        boolean hrSpike = false;

        for (PatientData data : readings) {
            if (data.getOxygenLevel() < 90) {
                spO2Drops++;
            }
        }

        // Check for HR spikes (sudden increase of >10 bpm)
        for (int i = 1; i < readings.size(); i++) {
            if (readings.get(i).getHeartRate() - readings.get(i - 1).getHeartRate() > 10) {
                hrSpike = true;
                break;
            }
        }

        return spO2Drops >= 3 && hrSpike; // At least 3 SpO₂ drops and 1 HR spike
    }

    private static boolean isEarlyHypoxiaWarning(List<PatientData> readings) {
        int hrIncrease = readings.get(readings.size() - 1).getHeartRate() - readings.get(0).getHeartRate();
        int spO2Decrease = readings.get(0).getOxygenLevel() - readings.get(readings.size() - 1).getOxygenLevel();

        return hrIncrease > 5 && spO2Decrease > 2; // HR increases by >5 bpm and SpO₂ drops by >2%
    }
}