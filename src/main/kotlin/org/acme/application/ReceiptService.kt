package org.acme.application

import jakarta.enterprise.context.ApplicationScoped
import org.acme.domain.model.Receipt
import org.acme.domain.repository.ReceiptRepository
import org.acme.infrastructure.vision.VisionService
import java.util.regex.Pattern

@ApplicationScoped
class ReceiptService(
    private val receiptRepository: ReceiptRepository,
    private val visionService: VisionService
) {
    fun processReceipt(imagePath: String) {
        val extractedText = visionService.analyzeImage(imagePath)

        // ここでテキストを解析して、店舗名、金額、日付などを動的に取得
        val storeName = extractStoreName(extractedText)
        val totalPrice = extractTotalPrice(extractedText)
        val date = extractDate(extractedText)

        // 解析した情報を使ってReceiptオブジェクトを作成
        val receipt = Receipt(storeName = storeName, totalPrice = totalPrice, date = date)

        // データベースに保存
        receiptRepository.save(receipt)
    }

    // 店舗名を抽出するメソッド
    private fun extractStoreName(text: String): String {
        val storeNamePattern = "Store Name: (\\S+)"  // 仮の正規表現（例: "Store Name: SuperMart"）
        val matcher = Pattern.compile(storeNamePattern).matcher(text)
        return if (matcher.find()) matcher.group(1) else "Unknown Store"
    }

    // 金額を抽出するメソッド（日本円対応）
    private fun extractTotalPrice(text: String): Double {
        val amountPattern = "¥([0-9,]+)"  // 修正: "¥1,234" の形式に対応
        val matcher = Pattern.compile(amountPattern).matcher(text)
        return if (matcher.find()) matcher.group(1).replace(",", "").toDouble() else 0.0
    }

    // 日付を抽出するメソッド
    private fun extractDate(text: String): String {
        val datePattern = "Date: (\\d{4}-\\d{2}-\\d{2})"  // 仮の正規表現（例: "Date: 2024-12-07"）
        val matcher = Pattern.compile(datePattern).matcher(text)
        return if (matcher.find()) matcher.group(1) else "Unknown Date"
    }
}
