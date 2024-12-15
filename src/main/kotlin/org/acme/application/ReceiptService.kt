package org.acme.application

import com.google.api.core.ApiFuture
import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.acme.domain.model.Receipt
import org.acme.domain.repository.ReceiptRepository
import org.acme.grpc.vision.VisionService
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@ApplicationScoped
class ReceiptService(
    private val receiptRepository: ReceiptRepository,
    private val visionService: VisionService
) {
    fun processReceipt(imageStream: InputStream, storeName: String, totalPrice: Int?, date: String?, item: List<Map<String, Any>>) {
        Log.info("Starting receipt processing")

        val tempFile = createTempFileFromInputStream(imageStream)

        // OCRで画像からテキストを抽出
        val extractedText = analyzeImageWithVisionService(tempFile.absolutePath)
        Log.info("Extracted Text: $extractedText")

        // Receiptオブジェクトを保存
        val receipt = Receipt(storeName, totalPrice, date, item)
        saveReceiptToFirestore(receipt)
    }

    fun saveReceiptToFirestore(receipt: Receipt) {
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

    // 一時ファイルを作成
    private fun createTempFileFromInputStream(inputStream: InputStream): File {
        val tempFile = File.createTempFile("receipt", ".jpg")
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        return tempFile
    }

    private fun analyzeImageWithVisionService(imagePath: String): String {
        return try {
            visionService.analyzeImage(imagePath)
        } catch (e: Exception) {
            Log.error("Failed to analyze image: ${e.message}", e)
            throw IllegalArgumentException("Failed to analyze the receipt image.")
        }
    }
}
