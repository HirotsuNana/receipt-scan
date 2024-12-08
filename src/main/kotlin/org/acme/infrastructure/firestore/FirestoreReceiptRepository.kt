package org.acme.infrastructure.firestore

import org.acme.domain.repository.ReceiptRepository
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import jakarta.enterprise.context.ApplicationScoped
import org.acme.domain.model.Receipt

@ApplicationScoped
class FirestoreReceiptRepository : ReceiptRepository {

    private val firestore: Firestore = FirestoreOptions.getDefaultInstance().service

    override fun save(receipt: Receipt) {
        // "receipts" コレクションに新しいレシートを追加
        val docRef = firestore.collection("receipts").document()
        docRef.set(receipt)
    }
}
