import sys
import numpy as np
import tensorflow as tf
from google.cloud import storage
import json
import os

# Firebase Cloud Storage bucket name
BUCKET_NAME = "capstone-a4ffe.firebasestorage.app"

def download_model(patient_id):
    """Downloads the trained LSTM model from Cloud Storage."""
    storage_client = storage.Client()
    bucket = storage_client.bucket(BUCKET_NAME)
    model_path = f"models/lstm_model_{patient_id}.h5"
    
    local_model_path = f"/tmp/lstm_model_{patient_id}.h5"
    
    blob = bucket.blob(model_path)
    
    if not blob.exists():
        print("NO_MODEL")
        sys.exit(0)
    
    blob.download_to_filename(local_model_path)
    return local_model_path

def preprocess_data(new_heart_rate):
    """Prepares the input data for the LSTM model."""
    # Convert input to a NumPy array (assuming we use the last 10 readings)
    sequence_length = 10  # Number of past readings used for prediction
    dummy_past_data = np.random.randint(60, 100, size=(sequence_length - 1)).tolist()
    data = dummy_past_data + [new_heart_rate]
    
    # Normalize between 0 and 1 (adjust normalization if needed)
    min_val, max_val = 40, 180  # Approximate human heart rate range
    normalized_data = [(x - min_val) / (max_val - min_val) for x in data]
    
    # Reshape for LSTM input (batch_size=1, time_steps=10, features=1)
    return np.array(normalized_data).reshape(1, sequence_length, 1)

def predict_anomaly(patient_id, new_heart_rate):
    """Runs the prediction and checks for anomalies."""
    # Download the model
    model_path = download_model(patient_id)
    
    # Load the LSTM model
    model = tf.keras.models.load_model(model_path)
    
    # Prepare the data
    input_data = preprocess_data(new_heart_rate)
    
    # Make a prediction
    predicted_hr = model.predict(input_data)[0][0]
    
    # Define an anomaly detection threshold
    THRESHOLD = 20  # Adjust based on model performance
    
    if abs(predicted_hr - new_heart_rate) > THRESHOLD:
        print("ALERT")
    else:
        print("NORMAL")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python3 predict_lstm.py <patient_id> <new_heart_rate>")
        sys.exit(1)

    patient_id = sys.argv[1]
    new_heart_rate = float(sys.argv[2])

    predict_anomaly(patient_id, new_heart_rate)
