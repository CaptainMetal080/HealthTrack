package com.example.healthtrack;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.healthtrack.R;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.UUID;

public class PatientHealthData extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private BluetoothDevice mDevice;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private final String DEVICE_ADDRESS = "bc:b5:a2:5b:02:16"; // Your HC-05 Bluetooth MAC address
    private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Standard SPP UUID
    private TextView heartRateTextView;
    private Button connectButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_health);

        // Find views
        heartRateTextView = findViewById(R.id.heartRateTextView);


        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth not available or not enabled", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            connectToBluetoothDevice();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    // Method to connect to Bluetooth device :)
    private void connectToBluetoothDevice() {
        // Check if Bluetooth permissions are granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // If permissions are not granted, request them
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    1); // 1 is the request code, you can choose any number
            return;
        }

        // If permissions are granted, proceed with Bluetooth connection
        mDevice = mBluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
        try {
            mSocket = mDevice.createRfcommSocketToServiceRecord(MY_UUID);
            mSocket.connect();
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();

            //listenForData();  // Start listening for data
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show();
        }
    }

    // Method to listen for incoming data from Bluetooth
//    private void listenForData() {
//        final Handler handler = new Handler();
//        final byte[] buffer = new byte[1024];  // Buffer to store incoming data
//        final int[] bytes = {0};
//
//        Thread listenThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        bytes[0] = mInputStream.read(buffer);
//                        final String data = new String(buffer, 0, bytes[0]);
//
//                        // Check if the data contains the keyword "BPM:"
//                        if (data.contains("BPM")) {
//                            // Extract BPM value from the received string
//                            final String bpm = data.replace("BPM: ", "").trim();
//
//                            // Update the UI with the heart rate data
//                            handler.post(new Runnable() {
//                                @Override
//                                public void run() {
//                                    heartRateTextView.setText("Heart Rate: " + bpm + " BPM");
//                                }
//                            });
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });
//
//        listenThread.start();
//    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mSocket != null) {
                mSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Handle the result of permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) { // Check if this is the permission request for Bluetooth Connect
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with Bluetooth connection
                connectToBluetoothDevice();
            } else {
                // Permission denied, inform the user
                Toast.makeText(this, "Bluetooth permission is required to connect to the device", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
