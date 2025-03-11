const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {Storage} = require("@google-cloud/storage");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const {onRequest} = require("firebase-functions/v2/https");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {setGlobalOptions} = require("firebase-functions/v2");
const fs = require("fs");
const {exec} = require("child_process");

initializeApp();
const db = getFirestore();
const storage = new Storage();

const BUCKET_NAME = "capstone-a4ffe.firebasestorage.app";

// Set global options
setGlobalOptions({
  maxInstances: 10,
  timeoutSeconds: 540,
  memory: "1GB",
});

/**
 * Cloud Function: Train LSTM model for each patient every 12 hours
 */
exports.trainLSTM = onSchedule("every 12 hours", async (context) => {
  console.log("Starting LSTM training for all patients...");

  const patientsRef = db.collection("patient_collection");
  const patients = await patientsRef.get();

  for (const patient of patients.docs) {
    const patientId = patient.id;
    console.log(`Processing patient: ${patientId}`);

    // Fetch health records
    const recordsRef = patientsRef
        .doc(patientId)
        .collection("health_records");
    const healthRecords = await recordsRef.orderBy("__name__").get();
    const data = [];

    healthRecords.forEach((doc) => {
      data.push({
        timestamp: doc.id,
        heartRate: doc.data().heartRate,
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

    console.log(`Uploaded dataset for ${patientId}. Starting training...`);

    // Trigger Vertex AI Training Job
    const cmd = [
      "gcloud ai custom-jobs create",
      "--region=us-central1",
      `--display-name=train-lstm-${patientId}`,
      `--python-package-uris=gs://${BUCKET_NAME}/train_lstm.py`,
      "--python-module=train_lstm",
      `--args=gs://${BUCKET_NAME}/datasets/${patientId}.json,${patientId}`,
    ].join(" ");

    exec(cmd, (error, stdout, stderr) => {
      if (error) {
        console.error(`Error executing command: ${error.message}`);
        return;
      }
      console.log(`Vertex AI training triggered for ${patientId}.`);
    });
  }

  return null;
});

/**
 * Cloud Function: Predict anomalies when new heart rate data is added
 */
exports.detectAnomalies = onDocumentCreated(
    "patient_collection/{patientId}/health_records/{recordId}",
    async (event) => {
      const patientId = event.params.patientId;
      const newHeartRate = event.data.data().heartRate;

      console.log(`New heart rate data for patient ${patientId}: ${newHeartRate}`);

      // Load trained model from Cloud Storage
      const modelPath = `models/lstm_model_${patientId}.h5`;
      const modelFile = storage.bucket(BUCKET_NAME).file(modelPath);

      if (!(await modelFile.exists())[0]) {
        console.log(`No trained model found for ${patientId}, skipping anomaly detection.`);
        return null;
      }

      console.log(`Trained model found for ${patientId}, analyzing data...`);

      // Call the prediction script with absolute path
      const scriptPath = `${process.cwd()}/machinelearning/predict_lstm.py`;
      
      return new Promise((resolve, reject) => {
        exec(
            `python3 ${scriptPath} ${patientId} ${newHeartRate}`,
            async (error, stdout, stderr) => {
              if (error) {
                console.error(`Error executing prediction script: ${error.message}`);
                reject(error);
                return;
              }

              const output = stdout.trim();
              console.log(`Prediction result: ${output}`);

              // Handle different prediction outcomes
              switch(output) {
                case "INVALID_HR":
                  console.warn(`Invalid heart rate detected for patient ${patientId}: ${newHeartRate}`);
                  await db.collection("alerts").add({
                    patientId: patientId,
                    timestamp: new Date(),
                    type: "error",
                    message: "Invalid heart rate reading detected",
                    value: newHeartRate
                  });
                  break;

                case "NO_HISTORY":
                  console.log(`Insufficient history for patient ${patientId}, skipping anomaly detection`);
                  break;

                case "NO_MODEL":
                  console.log(`No model available for patient ${patientId}, skipping anomaly detection`);
                  break;

                case "ALERT":
                  console.log(`Anomaly detected for patient ${patientId}, sending alert`);
                  await db.collection("alerts").add({
                    patientId: patientId,
                    timestamp: new Date(),
                    type: "anomaly",
                    message: "Abnormal heart rate pattern detected",
                    value: newHeartRate
                  });
                  break;

                case "NORMAL":
                  console.log(`Normal heart rate pattern for patient ${patientId}`);
                  break;

                default:
                  console.warn(`Unexpected output from prediction script: ${output}`);
              }

              resolve();
            }
        );
      });
    });

/**
 * HTTP endpoint to manually trigger LSTM training
 */
exports.triggerTraining = onRequest(async (req, res) => {
  console.log("Manual trigger for LSTM training...");

  const patientsRef = db.collection("patient_collection");
  const patients = await patientsRef.get();

  for (const patient of patients.docs) {
    const patientId = patient.id;
    console.log(`Processing patient: ${patientId}`);

    // Fetch health records
    const recordsRef = patientsRef
        .doc(patientId)
        .collection("health_records");
    const healthRecords = await recordsRef.orderBy("__name__").get();
    const data = [];

    healthRecords.forEach((doc) => {
      data.push({
        timestamp: doc.id,
        heartRate: doc.data().heartRate,
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

    console.log(`Uploaded dataset for ${patientId}. Starting training...`);

    // Trigger Vertex AI Training Job
    const cmd = [
      "gcloud ai custom-jobs create",
      "--region=us-central1",
      `--display-name=train-lstm-${patientId}`,
      `--python-package-uris=gs://${BUCKET_NAME}/train_lstm.py`,
      "--python-module=train_lstm",
      `--args=gs://${BUCKET_NAME}/datasets/${patientId}.json,${patientId}`,
    ].join(" ");

    exec(cmd, (error, stdout, stderr) => {
      if (error) {
        console.error(`Error executing command: ${error.message}`);
        return;
      }
      console.log(`Vertex AI training triggered for ${patientId}.`);
    });
  }

  res.json({message: "Training jobs triggered successfully"});
});
