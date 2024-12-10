import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.ImageSource
import com.google.cloud.vision.v1.TextAnnotation
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response
import org.acme.api.ReceiptUploadForm
import org.acme.application.ReceiptService
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Path("/scan")
class ReceiptScanResource {

    private val logger = LoggerFactory.getLogger(ReceiptScanResource::class.java)

    @Inject
    lateinit var receiptService: ReceiptService

    @Inject
    lateinit var storageService: StorageService  // ここでStorageServiceをインジェクト

    @POST
    @Consumes("multipart/form-data")
    fun scanReceipt(@MultipartForm form: ReceiptUploadForm): Response {
        return try {
            // ファイルストリームとファイル名を取得
            val file: InputStream = form.image
            val fileName = form.fileName ?: "receipt-${System.currentTimeMillis()}.jpg"

            logger.info("Image file received: $fileName")

            // Cloud Storageに画像をアップロード
            val imageUrl = storageService.generateSignedUrl("receipt-scanner-bucket", "receipts/$fileName")  // StorageServiceを使う
            logger.info("Image uploaded to Cloud Storage: $imageUrl")

            // Vision APIでテキストを抽出
            val extractedText = extractTextFromImage(imageUrl.toString())
            logger.info("Extracted text: $extractedText")

            // Firestoreにテキストを非同期で保存
            saveTextToFirestore(extractedText)

            Response.ok("Text extracted and saved successfully.").build()
        } catch (e: Exception) {
            logger.error("Error during receipt scanning: ${e.message}")
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to process image: ${e.message}")
                .build()
        }
    }

    // Cloud Storageへのアップロード（StorageServiceを使って署名付きURLを生成）
    private fun uploadImageToStorage(file: InputStream, fileName: String): String {
        // StorageServiceが直接画像をアップロードして署名付きURLを返す
        val signedUrl = storageService.generateSignedUrl("receipt-scanner-bucket", "receipts/$fileName")
        return signedUrl.toString()
    }

    // Vision APIで画像からテキストを抽出
    private fun extractTextFromImage(imageUrl: String): String {
        val client = ImageAnnotatorClient.create()

        try {
            // 画像URLから画像を読み込む
            val imageSource = ImageSource.newBuilder().setImageUri(imageUrl).build()
            val image = Image.newBuilder().setSource(imageSource).build()

            val feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build()

            val request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(image)
                .build()

            val response = client.batchAnnotateImages(listOf(request))

            // レスポンスの各アイテムにエラーがあるかチェック
            for (res in response.responsesList) {
                if (res.hasError()) {
                    val errorMessage = res.error.message
                    logger.error("Vision API Error: $errorMessage")
                    throw RuntimeException("Vision API failed with error: $errorMessage")
                }
            }

            val textAnnotation: TextAnnotation = response.responsesList[0].fullTextAnnotation
            return textAnnotation.text
        } catch (e: Exception) {
            logger.error("Error during text extraction: ${e.message}")
            throw RuntimeException("Failed to extract text from image: ${e.message}")
        } finally {
            client.close()
        }
    }

    // Firestoreにデータを非同期で保存
    private fun saveTextToFirestore(text: String) {
        val firestore = FirestoreOptions.getDefaultInstance().service
        val collectionRef = firestore.collection("receipts")

        val document = mapOf("extractedText" to text)

        // 非同期でデータを保存
        CompletableFuture.runAsync {
            try {
                collectionRef.add(document).get()  // Firestoreに非同期で保存
                logger.info("Data saved to Firestore successfully.")
            } catch (e: Exception) {
                logger.error("Error saving data to Firestore: ${e.message}")
                throw RuntimeException("Failed to save data to Firestore: ${e.message}")
            }
        }
    }
}
