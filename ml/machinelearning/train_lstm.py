import sys
import json
import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense
from sklearn.preprocessing import MinMaxScaler
from google.cloud import storage

BUCKET_NAME = "capstone-a4ffe.firebasestorage.app"

def fetch_data_from_storage(file_path):
    """Load patient heart rate data from Cloud Storage"""
    storage_client = storage.Client()
    bucket = storage_client.bucket(BUCKET_NAME)
    blob = bucket.blob(file_path)
    data = json.loads(blob.download_as_string())

    df = pd.DataFrame(data)
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    df.sort_values(by="timestamp", inplace=True)
    
    scaler = MinMaxScaler()
    df["heartRate"] = scaler.fit_transform(df[["heartRate"]])
    
    return df, scaler

def create_sequences(data, sequence_length=30):
    """Convert time-series data into sequences for LSTM."""
    sequences, labels = [], []
    for i in range(len(data) - sequence_length):
        sequences.append(data[i : i + sequence_length])
        labels.append(data[i + sequence_length])
    return np.array(sequences), np.array(labels)

def build_lstm_model(input_shape):
    """Build an LSTM model."""
    model = Sequential([
        LSTM(50, return_sequences=True, input_shape=input_shape),
        LSTM(50, return_sequences=False),
        Dense(1)
    ])
    model.compile(optimizer="adam", loss="mse")
    return model

def train_and_upload_model(patient_id, file_path):
    """Train an LSTM model for the given patient and upload it to Cloud Storage."""
    df, scaler = fetch_data_from_storage(file_path)
    X, y = create_sequences(df["heartRate"].values)
    X = X.reshape((X.shape[0], X.shape[1], 1))  # Reshape for LSTM
    
    model = build_lstm_model((X.shape[1], 1))
    model.fit(X, y, epochs=10, batch_size=16)
    
    # Save and upload the model
    local_model_path = f"/tmp/lstm_model_{patient_id}.h5"
    model.save(local_model_path)

    storage_client = storage.Client()
    bucket = storage_client.bucket(BUCKET_NAME)
    blob = bucket.blob(f"models/lstm_model_{patient_id}.h5")
    blob.upload_from_filename(local_model_path)

    print(f"Model uploaded to gs://{BUCKET_NAME}/models/lstm_model_{patient_id}.h5")

if __name__ == "__main__":
    file_path, patient_id = sys.argv[1], sys.argv[2]
    train_and_upload_model(patient_id, file_path)
