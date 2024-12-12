package org.acme.api

import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.protobuf.ByteString
import io.quarkus.logging.Log
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

@Path("/scan")
class ReceiptScanResource {

    @POST
    @Consumes("multipart/form-data")
    fun scanReceipt(@MultipartForm form: ReceiptUploadForm): Response {
        val file: InputStream = form.image
        try {
            val imageBytes = file.readBytes() // ストリームをバイト配列に変換
            Log.info("Image size: ${imageBytes.size} bytes") // デバッグログを追加
            if (!isValidImage(ByteArrayInputStream(imageBytes))) {
                Log.error("No image file provided or the file is empty.")
                return createBadRequestResponse("No image file provided or the file is empty.")
            }

            val extractedText = extractTextFromImage(ByteArrayInputStream(imageBytes))
            Log.info("Extracted text: $extractedText")

            saveTextToFirestore(extractedText)
            return Response.ok("Text extracted and saved successfully.").build()
        } catch (e: Exception) {
            Log.error("Error during receipt scanning: ${e.message}")
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
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

            val resizedImageBytes = resizeImage(originalImageBytes)

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
        } finally {
            client.close()
        }
    }

    private fun saveTextToFirestore(text: String) {
        val firestore = FirestoreOptions.getDefaultInstance().service
        val collectionRef = firestore.collection("receipts")
        val document = mapOf("extractedText" to text)

        CompletableFuture.runAsync {
            try {
                collectionRef.add(document).get()
                Log.info("Data saved to Firestore successfully.")
            } catch (e: Exception) {
                Log.error("Error saving data to Firestore: ${e.message}")
                throw RuntimeException("Failed to save data to Firestore: ${e.message}")
            }
        }
    }

    private fun resizeImage(imageBytes: ByteString): ByteString {
        try {
            // ByteStringをBufferedImageに変換
            val bufferedImage = ImageIO.read(ByteArrayInputStream(imageBytes.toByteArray()))

            // 画像のサイズを取得
            val originalWidth = bufferedImage.width
            val originalHeight = bufferedImage.height

            // 新しいサイズを計算 (ここでは50%に縮小)
            val newWidth = (originalWidth * 0.5).toInt()
            val newHeight = (originalHeight * 0.5).toInt()

            // 画像をリサイズ
            val resizedImage = BufferedImage(newWidth, newHeight, bufferedImage.type)
            val graphics2D = resizedImage.graphics
            graphics2D.drawImage(bufferedImage, 0, 0, newWidth, newHeight, null)
            graphics2D.dispose()

            // リサイズした画像をByteArrayOutputStreamに書き込む
            val baos = ByteArrayOutputStream()
            ImageIO.write(resizedImage, "jpg", baos) // JPEG形式で保存 (品質調整も可能)
            return ByteString.copyFrom(baos.toByteArray())
        } catch (e: IOException) {
            throw RuntimeException("Error resizing image: ${e.message}", e)
        }
    }
}
