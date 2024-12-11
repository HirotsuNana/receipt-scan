package org.acme.infrastructure.firestore

import org.acme.domain.repository.ReceiptRepository
import com.google.cloud.firestore.Firestore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.acme.domain.model.Receipt

@ApplicationScoped
class FirestoreReceiptRepository : ReceiptRepository {

    @Inject
    lateinit var firestore: Firestore  // Firestore をインジェクト

    override fun save(receipt: Receipt) {
        // "receipts" コレクションに新しいレシートを追加
        val docRef = firestore.collection("receipts").document()
        docRef.set(receipt)
    }
}
