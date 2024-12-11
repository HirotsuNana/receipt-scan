import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.Feature
import com.google.cloud.vision.v1.Image
import com.google.cloud.vision.v1.ImageSource
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class VisionService {

    private val visionClient: ImageAnnotatorClient by lazy {
        ImageAnnotatorClient.create()
    }

    /**
     * 画像URLを使ってVision APIを呼び出し、テキストを抽出する
     */
    private fun analyzeImageFromUrl(imageUrl: String): String {
        return try {
            Log.info("Starting image analysis for URL: $imageUrl")

            // 画像を直接ダウンロードして解析
            val img = Image.newBuilder()
                .setSource(ImageSource.newBuilder().setImageUri(imageUrl))
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
     * 画像URLを使用してVision APIで解析する
     */
    fun analyzeImage(imageUrl: String): String {
        Log.info("Starting image analysis for image: $imageUrl")

        // Vision APIを使用して画像解析
        return analyzeImageFromUrl(imageUrl)
    }
}
