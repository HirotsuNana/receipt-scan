package org.acme.application

import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.acme.domain.model.Receipt
import org.acme.domain.repository.ReceiptRepository
import org.acme.domain.service.ReceiptDataExtractor
import java.io.InputStream

@ApplicationScoped
class ReceiptService(
    private val receiptDataExtractor: ReceiptDataExtractor,
    private val receiptRepository: ReceiptRepository // ReceiptRepositoryを注入
) {
    fun processReceipt(imageStream: InputStream): Receipt {
        Log.info("Starting receipt processing")

        // 画像の検証とOCR解析
        val extractedText = receiptDataExtractor.extractTextFromImage(imageStream)
        val normalizedText = receiptDataExtractor.normalizeText(extractedText)

        // レシートデータの抽出
        val receiptData = receiptDataExtractor.extractReceiptData(normalizedText)
        val storeName = receiptData["StoreName"].toString()
        val totalPrice = receiptData["TotalPrice"] as Int
        val date = receiptData["Date"].toString()
        val items = receiptData["Items"] as? List<Map<String, Any>> ?: emptyList()

        val receipt = Receipt(storeName, totalPrice, date, items)

        saveReceiptToRepository(receipt)

        return receipt
    }

    private fun saveReceiptToRepository(receipt: Receipt) {
        receiptRepository.save(receipt)
    }
}
