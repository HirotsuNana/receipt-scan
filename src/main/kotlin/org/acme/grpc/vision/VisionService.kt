package org.acme.grpc.vision

import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import jakarta.enterprise.context.ApplicationScoped
import java.io.File

@ApplicationScoped
class VisionService {

    private val client: ImageAnnotatorClient = ImageAnnotatorClient.create()

    fun analyzeImage(imagePath: String): String {
        val imgFile = File(imagePath)
        if (!imgFile.exists()) {
            throw IllegalArgumentException("Image file does not exist: $imagePath")
        }

        val imgBytes = ByteString.readFrom(imgFile.inputStream())
        val image = Image.newBuilder().setContent(imgBytes).build()

        val feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build()
        val request = AnnotateImageRequest.newBuilder()
            .addFeatures(feature)
            .setImage(image)
            .build()

        val response = client.batchAnnotateImages(
            BatchAnnotateImagesRequest.newBuilder().addRequests(request).build()
        )

        val resultBuilder = StringBuilder()
        for (res in response.responsesList) {
            if (res.hasError()) {
                throw IllegalArgumentException("Vision API Error: ${res.error.message}")
            } else {
                res.textAnnotationsList.forEach { annotation ->
                    resultBuilder.appendLine(annotation.description)
                }
            }
        }
        return resultBuilder.toString()
    }

    fun close() {
        client.close()
    }
}
