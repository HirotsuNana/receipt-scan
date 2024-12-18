package org.acme.application

import com.google.api.core.ApiFuture
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.acme.domain.model.Receipt
import org.acme.domain.service.ReceiptDataExtractor
import java.io.InputStream

@ApplicationScoped
class ReceiptService(
    private val receiptDataExtractor: ReceiptDataExtractor
) {
    fun processReceipt(imageStream: InputStream): Receipt {
        Log.info("Starting receipt processing")

        // 画像の検証とOCR解析
        val extractedText = receiptDataExtractor.extractTextFromImage(imageStream)  // Corrected here
        val normalizedText = receiptDataExtractor.normalizeText(extractedText)

        // レシートデータの抽出
        val receiptData = receiptDataExtractor.extractReceiptData(normalizedText)
        val storeName = receiptData["StoreName"]
        val totalPrice = receiptData["TotalPrice"]
        val date = receiptData["Date"]
        val items = receiptData["Items"] as? List<Map<String, Any>> ?: emptyList()


        // Receiptオブジェクトを保存
        val receipt = Receipt(storeName.toString(), totalPrice, date.toString(), items)
        saveReceiptToFirestore(receipt)

        return receipt
    }

    private fun saveReceiptToFirestore(receipt: Receipt) {
        val firestore: Firestore = FirestoreOptions.getDefaultInstance().service
        val receiptCollection = firestore.collection("receipts")

        val receiptData: Map<String, Any?> = hashMapOf(
            "storeName" to receipt.storeName,
            "totalPrice" to receipt.totalPrice,
            "date" to receipt.date,
            "items" to receipt.items
        )

        val apiFuture: ApiFuture<DocumentReference> = receiptCollection.add(receiptData)

        // 非同期操作を待機してDocumentReferenceを取得
        val documentReference: DocumentReference = apiFuture.get()
    }
}
