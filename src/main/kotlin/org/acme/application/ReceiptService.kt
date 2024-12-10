package org.acme.application

import VisionService
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.acme.domain.model.Receipt
import org.acme.domain.repository.ReceiptRepository
import java.util.regex.Pattern

@ApplicationScoped
class ReceiptService(
    private val receiptRepository: ReceiptRepository,
    private val visionService: VisionService
) {
    fun processReceipt(imagePath: String, imageName: String) {
        Log.info("Starting receipt processing for: $imagePath with name: $imageName")

        // Vision APIを使って画像からテキストを抽出
        val extractedText = try {
            visionService.analyzeImage(imagePath, imageName)
        } catch (e: Exception) {
            Log.error("Failed to analyze image: ${e.message}", e)
            throw IllegalArgumentException("Failed to analyze the receipt image.")
        }

        Log.info("Extracted Text: $extractedText")

        // テキストから情報を抽出し、Receiptオブジェクトを作成
        val storeName = extractStoreName(extractedText)
        val totalPrice = extractTotalPrice(extractedText)
        val date = extractDate(extractedText)

        Log.info("Store Name extracted: $storeName")
        Log.info("Total Price extracted: $totalPrice")
        Log.info("Date extracted: $date")

        // Receiptオブジェクトを保存
        val receipt = Receipt(storeName, totalPrice, date)
        try {
            receiptRepository.save(receipt)
            Log.info("Receipt saved successfully.")
        } catch (e: Exception) {
            Log.error("Failed to save receipt: ${e.message}", e)
            throw RuntimeException("Failed to save receipt.")
        }
    }

    // 店舗名を抽出するメソッド（正規表現を柔軟に修正）
    private fun extractStoreName(text: String): String {
        val storeNamePattern = "(?<=Store Name:\\s*)(\\S+)"  // より柔軟に修正
        val matcher = Pattern.compile(storeNamePattern).matcher(text)
        return if (matcher.find()) matcher.group(1) else "Unknown Store"
    }

    // 金額を抽出するメソッド（日本円対応、金額のパターンを柔軟に）
    private fun extractTotalPrice(text: String): Double {
        val amountPattern = "¥([0-9,]+(?:\\.\\d{1,2})?)"  // 修正: 金額に小数点も対応
        val matcher = Pattern.compile(amountPattern).matcher(text)
        return if (matcher.find()) {
            matcher.group(1).replace(",", "").toDouble()
        } else {
            0.0
        }
    }

    // 日付を抽出するメソッド（正規表現を柔軟に修正）
    private fun extractDate(text: String): String {
        val datePattern = "(\\d{4}-\\d{2}-\\d{2})"  // 日付の形式を柔軟に修正
        val matcher = Pattern.compile(datePattern).matcher(text)
        return if (matcher.find()) matcher.group(1) else "Unknown Date"
    }
}
