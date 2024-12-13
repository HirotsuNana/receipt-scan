package org.acme.application

import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.acme.domain.model.Receipt
import org.acme.domain.model.ReceiptItem
import org.acme.domain.repository.ReceiptRepository
import org.acme.grpc.vision.VisionService
import java.time.LocalDate
import java.util.regex.Pattern

@ApplicationScoped
class ReceiptService(
    private val receiptRepository: ReceiptRepository,
    private val visionService: VisionService
) {
    fun processReceipt(imagePath: String, imageName: String) {
        Log.info("Starting receipt processing for: $imagePath with name: $imageName")

        // OCRで画像からテキストを抽出
        val extractedText = analyzeImageWithVisionService(imagePath)
        Log.info("Extracted Text: $extractedText")

        // テキストから必要な情報を抽出
        Log.info("Starting extraction from text")
        val storeName = extractStoreName(extractedText)
        Log.info("Store Name extracted: $storeName")

        val totalPrice = extractTotalPrice(extractedText)
        Log.info("Total Price extracted: $totalPrice")

        val date = extractDate(extractedText)

        Log.info("Extracted values: StoreName = $storeName, TotalPrice = $totalPrice, Date = $date")

        // Receiptオブジェクトを保存
        saveReceipt(Receipt(storeName, totalPrice, date))
    }

    private fun analyzeImageWithVisionService(imagePath: String): String {
        return try {
            visionService.analyzeImage(imagePath)
        } catch (e: Exception) {
            Log.error("Failed to analyze image: ${e.message}", e)
            throw IllegalArgumentException("Failed to analyze the receipt image.")
        }
    }

    private fun saveReceipt(receipt: Receipt) {
        try {
            receiptRepository.save(receipt)
            Log.info("Saved Receipt: $receipt")
        } catch (e: Exception) {
            Log.error("Failed to save receipt: ${e.message}", e)
            throw RuntimeException("Failed to save receipt.")
        }
    }

    fun extractStoreName(text: String): String? {
        val storeNameRegex = Regex("^(.*?)(\\s|\\n|[A-Za-z0-9]){2,}") // 店名を抽出する正規表現
        val matchResult = storeNameRegex.find(text)
        return matchResult?.groups?.get(1)?.value?.trim()
    }

    // 合計金額を抽出する
    fun extractTotalPrice(text: String): Double? {
        val priceRegex = Regex("合計\\s*¥?(\\d{1,3}(?:,\\d{3})*)") // 合計金額の正規表現
        val matchResult = priceRegex.find(text)
        return matchResult?.groups?.get(1)?.value?.replace(",", "")?.toDoubleOrNull()
    }

    // 日付を抽出する
    fun extractDate(text: String): String? {
        val dateRegex = Regex("\\d{4}年\\d{2}月\\d{2}日")  // 日付を抽出する正規表現
        val matchResult = dateRegex.find(text)
        return matchResult?.value?.trim()
    }

    // レシートの内容から必要な情報をまとめて返す
    fun extractReceiptInfo(text: String): Map<String, Any?> {
        val storeName = extractStoreName(text)
        val totalPrice = extractTotalPrice(text)
        val date = extractDate(text)

        // 結果をマップでまとめて返す
        return mapOf(
            "storeName" to storeName,
            "totalPrice" to totalPrice,
            "date" to date
        )
    }
}
