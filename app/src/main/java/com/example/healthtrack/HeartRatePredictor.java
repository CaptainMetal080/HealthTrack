package com.example.healthtrack;

import android.content.Context;
import android.util.Log;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class HeartRatePredictor {
    private static final String TAG = "HeartRatePredictor";
    private Module module;
    private static final String MODEL_PATH = "ml/heart_predictor.pt";
    private final Context context;

    public HeartRatePredictor(Context context) {
        this.context = context;
        try {
            String modelPath = assetFilePath(MODEL_PATH);
            Log.d(TAG, "Attempting to load model from: " + modelPath);
            module = Module.load(modelPath);
            Log.d(TAG, "Model loaded successfully from: " + modelPath);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model from path " + MODEL_PATH + ": " + e.getMessage(), e);
            throw new RuntimeException("Failed to load PyTorch model", e);
        }
    }

    public float predict(List<Float> features) {
        if (features.size() != 10) {
            throw new IllegalArgumentException("Input must contain exactly 10 features");
        }

        // Convert features to float array
        float[] inputArray = new float[10];
        for (int i = 0; i < 10; i++) {
            inputArray[i] = features.get(i);
        }

        try {
            // Create input tensor for heart rate data - reshape to [batch_size, sequence_length, features]
            final Tensor inputTensor = Tensor.fromBlob(inputArray, new long[]{1, 10, 1});
            
            // Create user_ids tensor with Long type
            long[] userIdArray = new long[]{0L};
            final Tensor userIdsTensor = Tensor.fromBlob(userIdArray, new long[]{1});

            // Run inference with both tensors
            final Tensor outputTensor = module.forward(IValue.from(inputTensor), IValue.from(userIdsTensor)).toTensor();
            float[] outputs = outputTensor.getDataAsFloatArray();
            return outputs[0];
        } catch (Exception e) {
            Log.e(TAG, "Error during prediction: " + e.getMessage(), e);
            throw new RuntimeException("Failed to run prediction", e);
        }
    }

    private String assetFilePath(String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        // Create parent directories if they don't exist
        File parentDir = file.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (FileOutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    public void close() {
        if (module != null) {
            try {
                module.destroy();
            } catch (Exception e) {
                Log.e(TAG, "Error closing module: " + e.getMessage(), e);
            }
        }
    }
} 