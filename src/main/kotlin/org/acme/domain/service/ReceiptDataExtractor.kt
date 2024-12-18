package org.acme.domain.service

import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.protobuf.ByteString
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

@ApplicationScoped
class ReceiptDataExtractor {

    private val client = ImageAnnotatorClient.create()

    fun extractTextFromImage(imageStream: InputStream): String {
        try {
            val originalImageBytes = ByteString.readFrom(imageStream)
            if (originalImageBytes.isEmpty) {
                throw RuntimeException("Failed to read the image data.")
            }
            Log.info("Original image size: ${originalImageBytes.size()} bytes")

            val resizedImageBytes = resizeImage(originalImageBytes)
            Log.info("Resized image size: ${resizedImageBytes.size()} bytes")

            val image = Image.newBuilder().setContent(resizedImageBytes).build()
            val feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build()

            val request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(image)
                .build()

            val response = client.batchAnnotateImages(listOf(request))
            val textAnnotation = response.responsesList[0].fullTextAnnotation
            return textAnnotation.text
        } catch (e: Exception) {
            Log.error("Error during text extraction: ${e.message}")
            throw RuntimeException("Failed to extract text from image: ${e.message}")
        }
    }

    private fun resizeImage(originalImageBytes: ByteString): ByteString {
        try {
            val image = ImageIO.read(ByteArrayInputStream(originalImageBytes.toByteArray()))
                ?: throw RuntimeException("Failed to decode image.")

            val maxDimension = 1024
            val scale = maxOf(image.width, image.height) / maxDimension.toFloat()
            val newWidth = (image.width / scale).toInt()
            val newHeight = (image.height / scale).toInt()

            val resizedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB)
            val graphics = resizedImage.createGraphics()
            graphics.drawImage(image, 0, 0, newWidth, newHeight, null)
            graphics.dispose()

            val byteArrayOutputStream = ByteArrayOutputStream()
            ImageIO.write(resizedImage, "PNG", byteArrayOutputStream)
            return ByteString.copyFrom(byteArrayOutputStream.toByteArray())
        } catch (e: Exception) {
            Log.error("Error resizing image: ${e.message}")
            throw RuntimeException("Failed to resize image: ${e.message}")
        }
    }

    // 合計金額を抽出する
    private fun extractTotalPrice(text: String): Int? {
        // 合計金額を抽出する正規表現
        val priceRegex = Regex("合計\\s*¥?\\s*(\\d{1,3}(?:[ ,]\\d{3})*)")
        val matchResult = priceRegex.find(text)

        if (matchResult != null) {
            Log.info("Total price found: ${matchResult.groups[1]?.value}")
        } else {
            Log.info("No total price found.")
        }

        // 抽出した金額からスペースやカンマを削除
        val rawPrice = matchResult?.groups?.get(1)?.value?.replace("[ ,]".toRegex(), "")
        Log.info("Extracted raw price string: $rawPrice")

        return rawPrice?.toIntOrNull()
    }

    // 日付を抽出する
    private fun extractDate(text: String): String? {
        val dateRegex = Regex("\\d{4}年\\d{2}月\\d{2}日")  // 日付を抽出する正規表現
        val matchResult = dateRegex.find(text)
        return matchResult?.value?.trim()
    }

    private fun convertToStandardDateFormat(date: String): String {
        // 例: "2024年12月07日" -> "2024-12-07"
        val regex = Regex("(\\d{4})年(\\d{2})月(\\d{2})日")
        return regex.replace(date) { matchResult ->
            "${matchResult.groupValues[1]}-${matchResult.groupValues[2]}-${matchResult.groupValues[3]}"
        }
    }

    private fun extractItems(text: String): List<Map<String, Any>> {
        // 商品ID（6桁）、商品名、価格、数量（X点や3P）を抽出する正規表現
        val itemRegex = Regex("""(\d{6})\s+([^\d¥]+(?:\s+[^\d¥]+)*?)\s*(¥[\d,]+(?:\.\d{1,2})?)\s*(X\d+点|3P|)?""")

        val items = mutableListOf<Map<String, Any>>()

        // 正規表現でテキスト内の商品情報を抽出
        val matches = itemRegex.findAll(text)

        // 抽出した項目をリストに追加
        for (match in matches) {
            val itemCode = match.groupValues[1].trim()  // 商品コード
            val itemName = match.groupValues[2].trim()  // 商品名
            val price = match.groupValues[3].trim()  // 価格
            val quantity = match.groupValues[4].trim()  // 数量（X2点など）

            val itemInfo = mutableMapOf<String, Any>(
                "ItemCode" to itemCode,
                "ItemName" to itemName,
                "Price" to price
            )

            // 数量が存在する場合は Map に追加
            if (quantity.isNotEmpty()) {
                itemInfo["Quantity"] = quantity
            }

            items.add(itemInfo)
        }

        if (items.isEmpty()) {
            println("No items found in the text.")
        }

        return items
    }

    fun extractReceiptData(text: String): Map<String, Any> {
        val storeName = Regex("""いせやフーズクラブ""").find(text)?.value ?: "Unknown Store"
        val totalPrice = extractTotalPrice(text) ?: 0
        val items = extractItems(text)
        val date = extractDate(text)?.let { convertToStandardDateFormat(it) } ?: "Unknown Date"

        return mapOf(
            "StoreName" to storeName,
            "TotalPrice" to totalPrice,
            "Items" to items,
            "Date" to date
        )
    }

    fun normalizeText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ") // 複数の空白を1つにまとめる
            .replace(Regex("¥\\s*"), "¥") // ¥の後の余分な空白を削除
            .replace(Regex("X\\s*"), "X") // Xの後の余分な空白を削除
            .replace(",", "") // カンマを削除
            .replace("点 ", "点:") // 数量のマーカーを追加
            .trim() // 前後の空白を削除
    }
}
