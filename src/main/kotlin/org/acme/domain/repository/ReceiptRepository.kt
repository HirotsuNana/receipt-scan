package org.acme.domain.repository

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import org.acme.domain.model.Receipt

@ApplicationScoped
class ReceiptRepository {

    @Produces
    fun firestore(): Firestore {
        return FirestoreOptions.getDefaultInstance().service
    }

    fun save(receipt: Receipt) {
        val firestore = firestore()
        val collectionRef = firestore.collection("receipts")
        val documentRef = collectionRef.document()  // 自動的に新しいIDを生成
        val document = mapOf(
            "storeName" to receipt.storeName,
            "totalPrice" to receipt.totalPrice,
            "date" to receipt.date,
            "item" to receipt.items
        )

        documentRef.set(document)
    }
}
