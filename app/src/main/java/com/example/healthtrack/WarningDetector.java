package com.example.healthtrack;

import java.util.ArrayList;
import java.util.List;

public class WarningDetector {
    private static final int WINDOW_SIZE = 5; // Analyze last 5 readings

    // Threshold constants (adjust as needed)
    private static final int MIN_SPO2 = 90;
    private static final int MIN_SPO2_DROP_COUNT = 3;
    private static final int HR_SPIKE_THRESHOLD = 10;       // Sudden increase in HR between consecutive readings
    private static final int HR_TREND_THRESHOLD = 5;          // Overall HR increase threshold
    private static final int SPO2_TREND_THRESHOLD = 2;        // Overall SpO2 drop threshold
    private static final double HIGH_TEMPERATURE = 39.0;      // Fever threshold in Celsius
    private static final double LOW_TEMPERATURE = 35.0;       // Hypothermia threshold in Celsius
    private static final double TEMP_UPWARD_TREND = 0.5;        // Minimal upward trend to flag potential fever

    public static List<String> detectWarnings(List<PatientData> patientData,boolean isHeartRateAnomaly) {
        List<String> warnings = new ArrayList<>();
        if (patientData.size() < WINDOW_SIZE) {
            return warnings; // Not enough data to analyze
        }

        // Analyze only the last WINDOW_SIZE readings
        List<PatientData> lastReadings = patientData.subList(patientData.size() - WINDOW_SIZE, patientData.size());

        // Check for Sleep Apnea Risk (oxygen drops + heart rate spike)
        if (isSleepApneaRisk(lastReadings)) {
            warnings.add("Possible sleep apnea risk detected.");
        }

        // Check for Early Hypoxia Warning (overall trends in HR and oxygen levels)
        if (isEarlyHypoxiaWarning(lastReadings)) {
            warnings.add("Possible oxygen deprivation detected.");
        }

        // Check for Temperature Anomalies (fever, hypothermia, or upward trend)
        if (isTemperatureAnomaly(lastReadings)) {
            warnings.add("Temperature anomaly detected.");
        }

        if(isHeartRateAnomaly){
            warnings.add("Heart Rate anomaly detected.");
        }

        return warnings;
    }

    private static boolean isSleepApneaRisk(List<PatientData> readings) {
        int spO2Drops = 0;
        boolean hrSpikeDetected = false;

        // Count how many readings drop below the oxygen threshold
        for (PatientData data : readings) {
            if (data.getOxygenLevel() < MIN_SPO2) {
                spO2Drops++;
            }
        }

        // Check for a sudden heart rate spike (e.g., an increase of more than HR_SPIKE_THRESHOLD bpm)
        for (int i = 1; i < readings.size(); i++) {
            int diff = readings.get(i).getHeartRate() - readings.get(i - 1).getHeartRate();
            if (diff > HR_SPIKE_THRESHOLD) {
                hrSpikeDetected = true;
                break;
            }
        }

        return spO2Drops >= MIN_SPO2_DROP_COUNT && hrSpikeDetected;
    }

    private static boolean isEarlyHypoxiaWarning(List<PatientData> readings) {
        // Calculate overall difference between the first and last reading
        int hrIncreaseOverall = readings.get(readings.size() - 1).getHeartRate() - readings.get(0).getHeartRate();
        int spo2DecreaseOverall = readings.get(0).getOxygenLevel() - readings.get(readings.size() - 1).getOxygenLevel();

        // Additionally, sum up the incremental changes between consecutive readings for a more robust trend
        int totalHrIncrease = 0;
        int totalSpo2Decrease = 0;
        for (int i = 1; i < readings.size(); i++) {
            totalHrIncrease += Math.max(0, readings.get(i).getHeartRate() - readings.get(i - 1).getHeartRate());
            totalSpo2Decrease += Math.max(0, readings.get(i - 1).getOxygenLevel() - readings.get(i).getOxygenLevel());
        }

        return (hrIncreaseOverall > HR_TREND_THRESHOLD || totalHrIncrease > HR_TREND_THRESHOLD)
                && (spo2DecreaseOverall > SPO2_TREND_THRESHOLD || totalSpo2Decrease > SPO2_TREND_THRESHOLD);
    }

    private static boolean isTemperatureAnomaly(List<PatientData> readings) {
        boolean highTempDetected = false;
        boolean lowTempDetected = false;

        // Check each reading for abnormal temperature values
        for (PatientData data : readings) {
            if (data.getTemperature() >= HIGH_TEMPERATURE) {
                highTempDetected = true;
            }
            if (data.getTemperature() <= LOW_TEMPERATURE) {
                lowTempDetected = true;
            }
        }

        // Check if there is an upward trend in temperature over the window
        double tempIncrease = readings.get(readings.size() - 1).getTemperature() - readings.get(0).getTemperature();
        boolean upwardTrend = tempIncrease > TEMP_UPWARD_TREND;

        return highTempDetected || lowTempDetected || upwardTrend;
    }

}
