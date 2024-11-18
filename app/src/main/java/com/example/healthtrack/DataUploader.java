package com.example.healthtrack;
import android.content.Context;
import android.util.Log;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DataUploader {
    private DBHelper dbHelper;
    private ApiService apiService;

    public DataUploader(Context context) {
        dbHelper = new DBHelper(context);
        apiService = RetrofitClient.getClient().create(ApiService.class);
    }

    public void uploadPatientDataBatch() {
        // Get the last 10 records from the database
        List<PatientData> patientDataList = dbHelper.getLast10PatientData();

        if (!patientDataList.isEmpty()) {
            // Make the API call to upload the data
            for (PatientData patientData : patientDataList) {
                Call<Void> call = apiService.uploadHealthData(patientData);
                call.enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            // If successful, remove these records from the local database
                            dbHelper.deletePatientData(patientData.getpId());
                        } else {
                            // Handle the error
                            Log.e("Upload", "Failed to upload data: " + response.message());
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e("Upload", "Error: " + t.getMessage());
                    }
                });
            }
        }
    }
}

