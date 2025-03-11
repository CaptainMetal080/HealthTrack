package com.example.healthtrack;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
public class HealthDataSimulator {
    private DataUploader uploader;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Random random = new Random();
    private int healthyRateCount = 0;
    private int emergencyRateCount = 0;
    private int currentStressLevel = 10; // Initialize stress level at a mid-point

    public HealthDataSimulator(DataUploader uploader) {
        this.uploader = uploader;
    }

    private String incrementTimestamp(String timestamp, int seconds) {
        try {
            Date date = sdf.parse(timestamp);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.SECOND, seconds);
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            e.printStackTrace();
            return timestamp;
        }
    }
    private float generateFluctuatingTemperature() {
        return 35.5f + random.nextFloat() * (37.5f - 35.5f);
    }

    private int generateFluctuatingOxygenLevel() {
        return 95 + random.nextInt(5); // Oxygen fluctuates between 95 and 99
    }
    private void simulatePatientData(String uid, int patientType) {
        String timestamp = "2025-03-10 22:25:03"; // Start from last known timestamp
        int totalRecords = (1* 1 * 60 * 60) / 9; // 14 days, every 8-10 seconds (avg 9 sec)
        //int totalRecords = 30; // 14 days, every 8-10 seconds (avg 9 sec)
        for (int i = 0; i < totalRecords; i++) {
            int heartRate = generateHeartRate(patientType, i);
            float temperature = generateFluctuatingTemperature();
            int oxygenLevel = generateFluctuatingOxygenLevel();
            int stressLevel = generateStressLevel(heartRate, oxygenLevel, temperature);

            PatientData patientData = new PatientData(timestamp, heartRate, oxygenLevel, temperature, stressLevel);
            uploader.uploadPatientData(uid, patientData);

            timestamp = incrementTimestamp(timestamp, ThreadLocalRandom.current().nextInt(8, 11));
        }
    }

    private int generateHeartRate(int patientType, int index) {
        double mean, stdDev;
        switch (patientType) {
            case 1:
                mean = 65; stdDev = 5; // Normal HR range
                break;
            case 2:
                return irregularHeartRate(index, 80,  10); // Irregular fluctuations with spikes
            case 3:
                return gradualHeartRateSpike(index, 75, 10, 100, 120, 300); // Gradual rise in HR
            case 4:
                return gradualHeartRateDrop(index, 65, 10, 40, 300); // Gradual drop in HR
            default:
                mean = 70; stdDev = 5;
        }
        return (int) Math.max(30, Math.min(150, mean + stdDev * random.nextGaussian()));
    }

    private int gradualHeartRateSpike(int index, float baseMean, float baseStdDev, int spikeMin, int spikeMax, int duration) {
        double heartRate = generateNormalValue(baseMean, baseStdDev);
        if (index % (duration * 5) < duration) {
            double spikeProgress = (index % duration) / (double) duration;
            heartRate += spikeProgress * (random.nextInt(spikeMax - spikeMin) + spikeMin - baseMean);
        }
        return (int) Math.max(30, Math.min(150, heartRate));
    }
    private int gradualHeartRateDrop(int index, float baseMean, float baseStdDev, int dropMin, int duration) {
        double heartRate = generateNormalValue(baseMean, baseStdDev);
        if (index % (duration * 5) < duration) {
            double dropProgress = (index % duration) / (double) duration;
            heartRate -= dropProgress * (baseMean - dropMin);
        }
        return (int) Math.max(30, Math.min(150, heartRate));
    }

    private int irregularHeartRate(int index, double baseMean, double baseStdDev) {
        double baseFunction = baseMean + baseStdDev * Math.sin(index / 50.0);

        double spike = 0;
        for (int i = 0; i < 3; i++) {
            double amplitude = random.nextDouble() * 40 + 80;
            double mu = random.nextDouble() * 5000;
            double sigma = random.nextDouble() * 100 + 50;
            spike += amplitude * Math.exp(-Math.pow(index - mu, 2) / (2 * Math.pow(sigma, 2)));
        }

        return (int) Math.max(30, Math.min(150, baseFunction + spike));
    }

    private float generateNormalValue(float mean, float stdDev) {
        return (float) (mean + stdDev * random.nextGaussian());
    }

    private int generateStressLevel(int heartRate, int oxygenLevel, float temperature) {
        if (heartRate > 160 || heartRate < 50 || oxygenLevel < 90 || temperature > 40 || temperature < 35) {
            emergencyRateCount++;
            healthyRateCount = 0;
        } else {
            healthyRateCount++;
            emergencyRateCount = 0;
        }

        if (healthyRateCount >= 2) {
            healthyRateCount = 0;
            currentStressLevel = Math.max(0, currentStressLevel - random.nextInt(4) - 2);
        } else if (emergencyRateCount >= 2) {
            emergencyRateCount = 0;
            currentStressLevel = Math.min(100, currentStressLevel + random.nextInt(7) + 5);
        }

        return currentStressLevel;
    }
public void startSimulation() {
        simulatePatientData("tuxUG8ANAHgpsTpYwgrTBiXgFbm1", 1);
        simulatePatientData("UH5QR2MXyGO3PhkVGNORjM4dzT62", 2);
        simulatePatientData("Ugdp4eTJqogQNLmkLEpbKEAr7Kr1", 3);
        simulatePatientData("4Zb9phGcAbhZzWldh75sUwF1Rwg1", 4);
    }
}
