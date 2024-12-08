package org.acme.infrastructure.vision

import com.google.cloud.vision.v1.ImageAnnotatorClient
import com.google.cloud.vision.v1.AnnotateImageRequest
import com.google.cloud.vision.v1.Feature
import com.google.protobuf.ByteString
import jakarta.enterprise.context.ApplicationScoped
import java.nio.file.Files
import java.io.File

@ApplicationScoped
class VisionService {
    fun analyzeImage(imagePath: String): String {
        val image = ByteString.readFrom(Files.newInputStream(File(imagePath).toPath()))
        val img = com.google.cloud.vision.v1.Image.newBuilder().setContent(image).build()
        val feature = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build()
        val request = AnnotateImageRequest.newBuilder().addFeatures(feature).setImage(img).build()

        val client = ImageAnnotatorClient.create()
        val response = client.batchAnnotateImages(listOf(request))
        client.close()

        return response.responsesList[0].fullTextAnnotation.text
    }
}
