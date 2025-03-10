const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { Storage } = require("@google-cloud/storage");
const { exec } = require("child_process");
const fs = require("fs");

admin.initializeApp();
const db = admin.firestore();
const storage = new Storage();

const BUCKET_NAME = "capstone-a4ffe.firebasestorage.app"; // Change this to your Firebase project's bucket

/**
 * Cloud Function: Train LSTM model for each patient daily
 */
exports.trainLSTM = functions.pubsub.schedule("every 24 hours").onRun(async (context) => {
    console.log("Starting LSTM training for all patients...");

    const patientsRef = db.collection("patient_collection");
    const patients = await patientsRef.get();

    for (const patient of patients.docs) {
        const patientId = patient.id;
        console.log(`Processing patient: ${patientId}`);

        // Fetch health records
        const recordsRef = patientsRef.doc(patientId).collection("health_records");
        const healthRecords = await recordsRef.orderBy("__name__").get();
        let data = [];

        healthRecords.forEach((doc) => {
            data.push({
                timestamp: doc.id,
                heartRate: doc.data().heartRate
            });
        });

        if (data.length === 0) {
            console.log(`No data found for patient ${patientId}, skipping.`);
            continue;
        }

        // Save JSON data to a temp file
        const filePath = `/tmp/${patientId}.json`;
        fs.writeFileSync(filePath, JSON.stringify(data));

        // Upload JSON to Cloud Storage
        const bucket = storage.bucket(BUCKET_NAME);
        const file = bucket.file(`datasets/${patientId}.json`);
        await file.save(fs.createReadStream(filePath));

        console.log(`Uploaded dataset for ${patientId}. Now triggering Vertex AI training...`);

        // Trigger Vertex AI Training Job
        exec(
            `gcloud ai custom-jobs create --region=us-central1 --display-name=train-lstm-${patientId} ` +
            `--python-package-uris=gs://${BUCKET_NAME}/train_lstm.py ` +
            `--python-module=train_lstm ` +
            `--args=gs://${BUCKET_NAME}/datasets/${patientId}.json,${patientId}`,
            (error, stdout, stderr) => {
                if (error) {
                    console.error(`Training error: ${error.message}`);
                    return;
                }
                console.log(`Vertex AI training triggered for ${patientId}.`);
            }
        );
    }

    return null;
});

/**
 * Cloud Function: Predict anomalies when new heart rate data is added
 */
exports.detectAnomalies = functions.firestore
    .document("patient_collection/{patientId}/health_records/{recordId}")
    .onCreate(async (snapshot, context) => {
        const patientId = context.params.patientId;
        const newHeartRate = snapshot.data().heartRate;

        console.log(`New heart rate data for patient ${patientId}: ${newHeartRate}`);

        // Load trained model from Cloud Storage
        const modelPath = `models/lstm_model_${patientId}.h5`;
        const modelFile = storage.bucket(BUCKET_NAME).file(modelPath);

        if (!(await modelFile.exists())[0]) {
            console.log(`No trained model found for ${patientId}, skipping anomaly detection.`);
            return null;
        }

        console.log(`Trained model found for ${patientId}, analyzing data...`);

        // Call an external Python script to make predictions
        exec(
            `python3 predict_lstm.py ${patientId} ${newHeartRate}`,
            (error, stdout, stderr) => {
                if (error) {
                    console.error(`Prediction error: ${error.message}`);
                    return;
                }
                console.log(`Prediction result: ${stdout}`);

                if (stdout.includes("ALERT")) {
                    console.log(`Anomaly detected for patient ${patientId}, sending alert.`);
                    db.collection("alerts").add({
                        patientId: patientId,
                        timestamp: admin.firestore.Timestamp.now(),
                        message: "Abnormal heart rate detected!",
                    });
                }
            }
        );

        return null;
    });
