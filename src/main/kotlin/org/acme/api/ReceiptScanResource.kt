import com.google.cloud.firestore.Firestore
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
import jakarta.ws.rs.core.Response
import org.acme.api.ReceiptUploadForm
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Path("/scan")
class ReceiptScanResource {

    @Inject
    lateinit var firestore: Firestore

    @POST
    @Consumes("multipart/form-data")
    suspend fun scanReceipt(@MultipartForm form: ReceiptUploadForm): Response {
        // 非同期処理でのレスポンスの返し方に問題がないか再確認
        return try {
            val file = form.image
            val fileName = form.fileName ?: "receipt-${System.currentTimeMillis()}.jpg"

            Log.info("Image file received: $fileName")

            val extractedText = extractTextFromImage(file)
            Log.info("Extracted text: $extractedText")

            saveTextToFirestore(extractedText)

            Response.ok("Text extracted and saved successfully.").build()
        } catch (e: Exception) {
            Log.error("Error during receipt scanning: ${e.message}")
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to process image: ${e.message}")
                .build()
        }
    }

    // Extract text from image using Vision API
    private fun extractTextFromImage(imageStream: InputStream): String {
        val client = ImageAnnotatorClient.create()

        try {
            // Convert InputStream to ByteString
            val byteString = ByteString.readFrom(imageStream)

            // Create Image object from ByteString
            val image = Image.newBuilder().setContent(byteString).build()

            // Set up text detection feature
            val feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build()

            // Prepare the request for the Vision API
            val request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(image)
                .build()

            // Call the Vision API
            val response = client.batchAnnotateImages(listOf(request))

            // Check for errors in the response
            for (res in response.responsesList) {
                if (res.hasError()) {
                    val errorMessage = res.error.message
                    Log.error("Vision API Error: $errorMessage")
                    throw RuntimeException("Vision API failed with error: $errorMessage")
                }
            }

            // Extract and return text if available
            val annotations = response.responsesList[0].textAnnotationsList
            if (annotations.isNotEmpty()) {
                Log.info("Text extracted: ${annotations.first().description}")
                return annotations.first().description
            } else {
                Log.warn("No text found in image.")
                return "No text found"
            }
        } catch (e: Exception) {
            Log.error("Error during text extraction: ${e.message}", e)
            throw RuntimeException("Failed to extract text from image: ${e.message}")
        } finally {
            client.close()
        }
    }

    // Asynchronously save extracted text to Firestore using coroutines
    private suspend fun saveTextToFirestore(text: String) {
        withContext(Dispatchers.IO) {
            try {
                val collectionRef = firestore.collection("receipts")
                val document = mapOf("extractedText" to text)

                // Add the document asynchronously (non-blocking)
                collectionRef.add(document).get() // Blocking the coroutine context here but handled in IO thread
                Log.info("Data saved to Firestore successfully.")
            } catch (e: Exception) {
                Log.error("Error saving data to Firestore: ${e.message}")
            }
        }
    }
}
