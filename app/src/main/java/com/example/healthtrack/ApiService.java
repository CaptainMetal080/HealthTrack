package com.example.healthtrack;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    @POST("upload")
    Call<Void> uploadHealthData(@Body PatientData patientData);

    @GET("retrieve")
    Call<List<PatientData>> getHealthData(@Query("patient_id") Integer patientId);
}