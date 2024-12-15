package org.acme.api

import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.protobuf.ByteString
import io.quarkus.logging.Log
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.acme.application.ReceiptService
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

@Path("/scan")
class ReceiptScanResource {

    @Inject
    lateinit var receiptService: ReceiptService

    @POST
    @Consumes("multipart/form-data")
    @Produces(MediaType.APPLICATION_JSON)
    fun scanReceipt(@MultipartForm form: ReceiptUploadForm): Response {
        val file: InputStream = form.image
        return try {
            val imageBytes = file.readBytes()
            Log.info("Image size: ${imageBytes.size} bytes")

            if (!isValidImage(ByteArrayInputStream(imageBytes))) {
                Log.error("No image file provided or the file is empty.")
                return createBadRequestResponse("No image file provided or the file is empty.")
            }

            val extractedText = extractTextFromImage(ByteArrayInputStream(imageBytes))
            val normalizedText = normalizeText(extractedText)

            val receiptData = extractReceiptData(normalizedText)

            val storeName = receiptData["StoreName"] as? String
            val totalPrice = receiptData["TotalPrice"] as? Int
            val date = receiptData["Date"] as? String
            val items = receiptData["Items"] as List<Map<String, Any>>

            Log.info("Normalized Text: $normalizedText")
            Log.info("Extracted Items: $items")

            Log.info("Extracted values: StoreName = $storeName, TotalPrice = $totalPrice, Items = $items")

            storeName?.let {
                receiptService.processReceipt(ByteArrayInputStream(imageBytes), it, totalPrice, date, items)
                return Response.ok("Receipt processed successfully").build()
            }

            Log.error("Store name not found.")
            createBadRequestResponse("Store name not found.")
        } catch (e: Exception) {
            Log.error("Error during receipt scanning: ${e.message}")
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to process image: ${e.message}")
                .build()
        }
    }

    private fun isValidImage(image: InputStream): Boolean {
        return try {
            ImageIO.read(image) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun createBadRequestResponse(message: String): Response {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(message)
            .build()
    }

    private fun extractTextFromImage(imageStream: InputStream): String {
        val client = ImageAnnotatorClient.create()
        try {
            val originalImageBytes = ByteString.readFrom(imageStream)
            if (originalImageBytes.isEmpty) {
                throw RuntimeException("Failed to read the image data.")
            }
            Log.info("Original image size: ${originalImageBytes.size()} bytes")

            // リサイズ処理をここで行う
            val resizedImageBytes = resizeImage(originalImageBytes)
            Log.info("Resized image size: ${resizedImageBytes.size()} bytes")

            val image = Image.newBuilder().setContent(resizedImageBytes).build()
            val feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build()

            // Requestで画像と機能が正しく設定されているか確認
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
        } finally {
            client.close()
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
            val resizedImageBytes = ByteString.copyFrom(byteArrayOutputStream.toByteArray())

            Log.info("Resized image size: ${resizedImageBytes.size()} bytes")
            return resizedImageBytes
        } catch (e: Exception) {
            Log.error("Error resizing image: ${e.message}")
            throw RuntimeException("Failed to resize image: ${e.message}")
        }
    }

    // 合計金額を抽出する
    fun extractTotalPrice(text: String): Int? {
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
    fun extractDate(text: String): String? {
        val dateRegex = Regex("\\d{4}年\\d{2}月\\d{2}日")  // 日付を抽出する正規表現
        val matchResult = dateRegex.find(text)
        return matchResult?.value?.trim()
    }

    fun convertToStandardDateFormat(date: String): String {
        // 例: "2024年12月07日" -> "2024-12-07"
        val regex = Regex("(\\d{4})年(\\d{2})月(\\d{2})日")
        return regex.replace(date) { matchResult ->
            "${matchResult.groupValues[1]}-${matchResult.groupValues[2]}-${matchResult.groupValues[3]}"
        }
    }

    fun extractItems(text: String): List<String> {
        val itemRegex = Regex("""\d{6}\s+\D+?\s+¥(\d{1,3}(?:,\d{3})*)""") // 商品コード + 商品名 + 価格
        return itemRegex.findAll(text).map { it.value.trim() }.toList()
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
