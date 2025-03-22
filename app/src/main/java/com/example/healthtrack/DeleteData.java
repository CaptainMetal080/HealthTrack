package com.example.healthtrack;

import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

public class DeleteData {

    private FirebaseFirestore firestore;

    public DeleteData() {
        firestore = FirebaseFirestore.getInstance();
    }

    public void deleteOneHourOfData(String patientId, String startTimestamp) {
        try {
            // Parse the start timestamp
            // Calculate the end timestamp (1 hour later)
            String endDate = "2025-03-22 08:29:58";

            // Query to fetch documents within the one-hour window
            firestore.collection("patient_collection")
                    .document(patientId)
                    .collection("health_records")
                    .whereGreaterThanOrEqualTo(FieldPath.documentId(), startTimestamp)
                    .whereLessThanOrEqualTo(FieldPath.documentId(), endDate)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            QuerySnapshot querySnapshot = task.getResult();
                            if (querySnapshot != null && !querySnapshot.isEmpty()) {
                                for (QueryDocumentSnapshot document : querySnapshot) {
                                    // Delete each document within the time range
                                    document.getReference().delete()
                                            .addOnSuccessListener(aVoid -> {
                                                System.out.println("Deleted document: " + document.getId());
                                            })
                                            .addOnFailureListener(e -> {
                                                System.err.println("Error deleting document: " + document.getId() + ", Error: " + e.getMessage());
                                            });
                                }
                                System.out.println("All documents within the one-hour window have been deleted.");
                            } else {
                                System.out.println("No documents found within the specified time range.");
                            }
                        } else {
                            System.err.println("Error fetching documents: " + task.getException().getMessage());
                        }
                    });

        } catch (Exception e) {
            System.err.println("Error parsing timestamp or querying Firestore: " + e.getMessage());
        }
    }

    public static void main() {
        // Example usage
        String patientId = "tuxUG8ANAHgpsTpYwgrTBiXgFbm1"; // Replace with the actual patient ID
        String startTimestamp = "2025-03-22 08:09:35"; // Replace with the starting timestamp

        DeleteData deleter = new DeleteData();
        deleter.deleteOneHourOfData(patientId, startTimestamp);
    }
}