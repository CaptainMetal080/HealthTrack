import sys
import numpy as np
import tensorflow as tf
from google.cloud import storage
from google.cloud import firestore
import json
import os

# Firebase Cloud Storage bucket name
BUCKET_NAME = "capstone-a4ffe.firebasestorage.app"

# Constants for heart rate validation
MIN_VALID_HR = 30  # Minimum physiologically possible heart rate
MAX_VALID_HR = 220  # Maximum physiologically possible heart rate

def validate_heart_rate(heart_rate):
    """Validates if the heart rate is physiologically possible."""
    return MIN_VALID_HR <= heart_rate <= MAX_VALID_HR

def get_historical_data(patient_id, sequence_length=10):
    """Gets the most recent heart rate readings from Firestore."""
    db = firestore.Client()
    records_ref = db.collection("patient_collection").document(patient_id).collection("health_records")
    
    # Get the last sequence_length records
    query = records_ref.order_by("__name__", direction=firestore.Query.DESCENDING).limit(sequence_length)
    docs = query.get()
    
    # Convert to list and reverse to get chronological order
    heart_rates = [doc.get("heartRate") for doc in docs]
    heart_rates.reverse()
    
    return heart_rates

def download_model(patient_id):
    """Downloads the trained model from Cloud Storage."""
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

def preprocess_data(heart_rates, new_heart_rate):
    """Prepares the input data for the model."""
    sequence_length = 10  # Number of past readings used for prediction
    
    # Combine historical data with new reading
    if len(heart_rates) < sequence_length - 1:
        # If not enough historical data, pad with the mean
        mean_hr = sum(heart_rates) / len(heart_rates)
        heart_rates = [mean_hr] * (sequence_length - 1 - len(heart_rates)) + heart_rates
    else:
        # Take the last sequence_length - 1 readings
        heart_rates = heart_rates[-(sequence_length - 1):]
    
    data = heart_rates + [new_heart_rate]
    
    # Calculate dynamic normalization range based on data
    min_val = min(data) - 10  # Add padding to range
    max_val = max(data) + 10
    
    # Normalize between 0 and 1
    normalized_data = [(x - min_val) / (max_val - min_val) for x in data]
    
    # Reshape for model input (batch_size=1, time_steps=10, features=1)
    return np.array(normalized_data).reshape(1, sequence_length, 1)

def check_rapid_change(heart_rates, new_heart_rate):
    """Checks for rapid changes in heart rate."""
    if not heart_rates:
        return False
    
    last_hr = heart_rates[-1]
    change_rate = abs(new_heart_rate - last_hr)
    return change_rate > 30  # Alert if change is more than 30 bpm in one reading

def predict_anomaly(patient_id, new_heart_rate):
    """Runs the prediction and checks for anomalies."""
    # First validate the heart rate
    if not validate_heart_rate(new_heart_rate):
        print("INVALID_HR")
        sys.exit(0)
    
    # Get historical data
    heart_rates = get_historical_data(patient_id)
    
    if not heart_rates:
        print("NO_HISTORY")
        sys.exit(0)
    
    # Check for rapid changes
    if check_rapid_change(heart_rates, new_heart_rate):
        print("ALERT")
        return
    
    # Download the model
    model_path = download_model(patient_id)
    
    # Prepare the data
    input_data = preprocess_data(heart_rates, new_heart_rate)
    
    # Make prediction
    model = tf.keras.models.load_model(model_path)
    predicted_hr = model.predict(input_data)[0][0]
    
    # Calculate dynamic threshold based on historical data variance
    std_dev = np.std(heart_rates)
    recent_mean = np.mean(heart_rates[-3:])  # Mean of last 3 readings
    
    # Dynamic threshold based on both standard deviation and absolute difference
    base_threshold = max(20, 2 * std_dev)  # At least 20 bpm difference, or 2 standard deviations
    
    # Additional checks for anomaly detection
    is_anomaly = False
    
    # Check 1: Difference from prediction
    if abs(predicted_hr - new_heart_rate) > base_threshold:
        is_anomaly = True
    
    # Check 2: Deviation from recent mean
    if abs(new_heart_rate - recent_mean) > base_threshold * 1.5:
        is_anomaly = True
    
    # Check 3: Sustained high/low heart rate
    if len(heart_rates) >= 3:
        if all(hr > 100 for hr in heart_rates[-3:] + [new_heart_rate]):  # Sustained high HR
            is_anomaly = True
        if all(hr < 50 for hr in heart_rates[-3:] + [new_heart_rate]):   # Sustained low HR
            is_anomaly = True
    
    if is_anomaly:
        print("ALERT")
    else:
        print("NORMAL")
    
    # Clean up
    os.remove(model_path)

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python3 predict_lstm.py <patient_id> <new_heart_rate>")
        sys.exit(1)

    patient_id = sys.argv[1]
    new_heart_rate = float(sys.argv[2])

    predict_anomaly(patient_id, new_heart_rate)
