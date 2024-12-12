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

    private fun saveTextToFirestore(text: String) {
        val firestore = FirestoreOptions.getDefaultInstance().service
        val collectionRef = firestore.collection("receipts")
        val document = mapOf("extractedText" to text)

        CompletableFuture.runAsync {
            try {
                // 非同期処理を待機するように変更
                collectionRef.add(document).get()
                Log.info("Data saved to Firestore successfully.")
            } catch (e: Exception) {
                Log.error("Error saving data to Firestore: ${e.message}")
                throw RuntimeException("Failed to save data to Firestore: ${e.message}")
            }
        }.join()
    }

    private fun resizeImage(originalImageBytes: ByteString): ByteString {
        try {
            val image = ImageIO.read(ByteArrayInputStream(originalImageBytes.toByteArray()))
            if (image == null) {
                throw RuntimeException("Failed to decode image.")
            }

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

            // デバッグログを追加
            Log.info("Resized image size: ${resizedImageBytes.size()} bytes")
            return resizedImageBytes
        } catch (e: Exception) {
            Log.error("Error resizing image: ${e.message}")
            throw RuntimeException("Failed to resize image: ${e.message}")
        }
    }
}
