import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.Feature
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Blob
import com.google.cloud.storage.StorageOptions
import com.google.cloud.vision.v1.Image
import com.google.protobuf.ByteString
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.io.FileInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ApplicationScoped
class VisionService {

    private val storage: Storage = StorageOptions.getDefaultInstance().service
    private val bucketName = "receipt-scanner-bucket"

    @Inject
    lateinit var storageService: StorageService

    private val visionClient: ImageAnnotatorClient by lazy {
        ImageAnnotatorClient.create()
    }

    /**
     * 画像をCloud Storageにアップロードする
     */
    private fun uploadImageToCloudStorage(imagePath: String, imageName: String): Blob {
        return try {
            Log.info("Uploading image to Cloud Storage: $imageName")
            val imgBytes = ByteString.readFrom(FileInputStream(imagePath))
            val blob = storage.create(Blob.newBuilder(bucketName, imageName).build(), imgBytes.toByteArray())
            Log.info("Image uploaded successfully to $bucketName with name $imageName")
            blob
        } catch (e: Exception) {
            Log.error("Failed to upload image to Cloud Storage", e)
            throw RuntimeException("Failed to upload image to Cloud Storage", e)
        }
    }

    /**
     * 画像URLを使ってVision APIを呼び出し、テキストを抽出する
     */
    private fun analyzeImageFromUrl(imageUrl: String): String {
        return try {
            Log.info("Starting image analysis for URL: $imageUrl")
            val img = Image.newBuilder()
                .setSource(com.google.cloud.vision.v1.ImageSource.newBuilder().setImageUri(imageUrl))
                .build()
            val feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
            val request = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build()

            val response = visionClient.batchAnnotateImages(listOf(request))
            val annotations = response.responsesList[0].textAnnotationsList
            if (annotations.isNotEmpty()) {
                Log.info("Text extracted: ${annotations.first().description}")
                return annotations.first().description
            } else {
                Log.warn("No text found in image.")
                return "No text found"
            }
        } catch (e: Exception) {
            Log.error("Failed to analyze image from URL", e)
            throw RuntimeException("Failed to analyze image", e)
        }
    }

    /**
     * 画像をCloud Storageにアップロードし、Vision APIで解析する
     */
    fun analyzeImage(imagePath: String, imageName: String): String {
        // 画像をCloud Storageにアップロード
        Log.info("Starting image upload and analysis for image: $imageName")
        val blob = uploadImageToCloudStorage(imagePath, imageName)

        // 署名付きURLを生成
        val signedUrl = storageService.generateSignedUrl(bucketName, blob.name)
        Log.info("Generated signed URL: $signedUrl")

        val encodedUrl = URLEncoder.encode(signedUrl.toString(), StandardCharsets.UTF_8.toString())
        Log.info("Generated encoded URL: $encodedUrl")

        // Vision APIを使用して画像解析
        return analyzeImageFromUrl(encodedUrl)
    }
}
