package com.example.healthtrack;

import java.util.HashMap;
import java.util.Map;

public class AnomalyTracker {
    private Map<Integer, Boolean> anomalyMap = new HashMap<>();

    public void markAnomaly(int index) {
        anomalyMap.put(index, true);
    }

    public boolean isAnomaly(int index) {
        return anomalyMap.containsKey(index) && anomalyMap.get(index);
    }
}