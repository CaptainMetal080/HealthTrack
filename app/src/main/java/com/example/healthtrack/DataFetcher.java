package com.example.healthtrack;

import android.util.Log;

import com.example.healthtrack.ApiService;
import com.example.healthtrack.PatientData;
import com.example.healthtrack.RetrofitClient;

import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DataFetcher {
    private ApiService apiService;

    public DataFetcher() {
        apiService = RetrofitClient.getClient().create(ApiService.class);
    }

    public void fetchHealthData(Integer patientId) {
        // Fetch data with or without a patientId filter
        Call<List<PatientData>> call = apiService.getHealthData(patientId);
        call.enqueue(new Callback<List<PatientData>>() {
            @Override
            public void onResponse(Call<List<PatientData>> call, Response<List<PatientData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PatientData> data = response.body();
                    for (PatientData record : data) {
                        Log.d("DataFetcher", "Fetched record: " + record.toString());
                    }
                } else {
                    Log.e("DataFetcher", "Failed to fetch data: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<List<PatientData>> call, Throwable t) {
                Log.e("DataFetcher", "Error: " + t.getMessage());
            }
        });
    }
}
