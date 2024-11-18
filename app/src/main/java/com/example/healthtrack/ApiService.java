package com.example.healthtrack;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("uploadHealthData")
    Call<Void> uploadHealthData(@Body PatientData patientData);
}
