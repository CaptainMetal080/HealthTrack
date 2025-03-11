import tensorflow as tf
import numpy as np
import pandas as pd
from google.cloud import firestore, storage
import os

# Firestore and Cloud Storage setup
db = firestore.Client()
BUCKET_NAME = "capstone-a4ffe.firebasestorage.app"  # Change this to your actual bucket name

def get_all_patients():
    """Fetch all patient IDs from Firestore."""
    patients_ref = db.collection("patient_collection")
    docs = patients_ref.stream()
    return [doc.id for doc in docs]  # Extract patient IDs

def fetch_patient_data(patient_id):
    """Fetches all available heart rate data for a given patient."""
    health_ref = db.collection("patient_collection").document(patient_id).collection("health_records")
    docs = health_ref.stream()

    data = []
    for doc in docs:
        entry = doc.to_dict()
        if "heartRate" in entry:
            data.append(entry["heartRate"])

    return np.array(data) if data else None

def train_lstm_model(patient_id):
    """Trains an LSTM model on the patient's heart rate data."""
    data = fetch_patient_data(patient_id)
    if data is None or len(data) < 10:
        print(f"Not enough data for patient {patient_id}")
        return None

    # Normalize data
    min_val, max_val = 40, 180  # Define heart rate range
    data = (data - min_val) / (max_val - min_val)

    # Prepare dataset (sequence of 10 readings)
    sequence_length = 10
    X, y = [], []
    for i in range(len(data) - sequence_length):
        X.append(data[i:i+sequence_length])
        y.append(data[i+sequence_length])

    X, y = np.array(X), np.array(y)
    X = X.reshape((X.shape[0], X.shape[1], 1))

    # Build LSTM model
    model = tf.keras.Sequential([
        tf.keras.layers.LSTM(50, return_sequences=True, input_shape=(sequence_length, 1)),
        tf.keras.layers.LSTM(50),
        tf.keras.layers.Dense(1)
    ])
    
    model.compile(optimizer='adam', loss='mse')
    model.fit(X, y, epochs=10, batch_size=8, verbose=1)

    # Save model locally
    model_path = f"/tmp/lstm_model_{patient_id}.h5"
    model.save(model_path)
    
    return model_path

def upload_model(patient_id, model_path):
    """Uploads the trained LSTM model to Google Cloud Storage."""
    storage_client = storage.Client()
    bucket = storage_client.bucket(BUCKET_NAME)
    model_blob = bucket.blob(f"models/lstm_model_{patient_id}.h5")
    model_blob.upload_from_filename(model_path)
    print(f"Model uploaded to: gs://{BUCKET_NAME}/models/lstm_model_{patient_id}.h5")

if __name__ == "__main__":
    patient_ids = get_all_patients()  # Get all patient IDs from Firestore
    
    for patient_id in patient_ids:
        print(f"Training model for patient: {patient_id}")
        model_path = train_lstm_model(patient_id)
        if model_path:
            upload_model(patient_id, model_path)
