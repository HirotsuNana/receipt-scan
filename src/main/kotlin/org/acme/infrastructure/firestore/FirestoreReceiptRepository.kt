package org.acme.infrastructure.firestore

import com.google.api.core.ApiFuture
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import jakarta.enterprise.context.ApplicationScoped
import org.acme.domain.model.Receipt
import org.acme.domain.repository.ReceiptRepository

@ApplicationScoped
class FirestoreReceiptRepository : ReceiptRepository {

    private val firestore: Firestore = FirestoreOptions.getDefaultInstance().service
    private val receiptCollection = firestore.collection("receipts")

    override fun save(receipt: Receipt) {
        val receiptData = mapOf(
            "storeName" to receipt.storeName,
            "totalPrice" to receipt.totalPrice,
            "date" to receipt.date,
            "items" to receipt.items
        )

        val apiFuture: ApiFuture<DocumentReference> = receiptCollection.add(receiptData)
        apiFuture.get()
    }
}
