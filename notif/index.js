/* eslint-disable */
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();
exports.sendEmergencyNotification = functions.https.onCall((data, context) => {
  console.log("Cloud Function triggered with data:", JSON.stringify(data, null, 2));

  const { doctorFcmToken, title, message, patientId } = data;

  if (!doctorFcmToken || !title || !message || !patientId) {
    console.error("Missing required fields in data:", { doctorFcmToken, title, message, patientId });
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required fields"
    );
  }

  const payload = {
    notification: {
      title: title,
      body: message,
    },
    data: {
      patientId: patientId,
    },
  };

return admin
    .messaging()
    .send({ token: doctorFcmToken, ...payload })
    .then((response) => {
      console.log("Notification sent successfully:", response);
      return { success: true };
    })
    .catch((error) => {
      console.error("Error sending notification:", error);
      throw new functions.https.HttpsError("internal", "Failed to send notification");
    });
});