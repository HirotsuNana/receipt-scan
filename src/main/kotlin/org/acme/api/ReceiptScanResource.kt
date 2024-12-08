package org.acme.api

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.cloud.firestore.Firestore
import org.acme.application.ReceiptService
import jakarta.inject.Inject
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput
import java.io.InputStream

@Path("/scan")
class ReceiptScanResource {

    @Inject
    lateinit var receiptService: ReceiptService

    private val storage: Storage = StorageOptions.getDefaultInstance().service

    @POST
    @Consumes("multipart/form-data")
    fun scanReceipt(input: MultipartFormDataInput): Response {
        val formData = input.formDataMap
        val fileData = formData["image"]?.firstOrNull()

        if (fileData != null) {
            println("Image file received: ${fileData.headers}")
            val file: InputStream = fileData.body as InputStream
            val contentDisposition = fileData.headers["Content-Disposition"]?.firstOrNull()
            val fileName = contentDisposition?.substringAfter("filename=\"")?.substringBefore("\"") ?: "default_filename"

            // Cloud Storage に画像をアップロード
            val imageUrl = uploadImageToStorage(file, fileName)

            // Firestore に画像のメタデータを保存
            val receiptData = mapOf("imageUrl" to imageUrl)
            try {
                saveReceiptMetadataToFirestore(receiptData)
                return Response.ok("Image uploaded successfully. URL: $imageUrl").build()
            } catch (e: Exception) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to save receipt metadata: ${e.message}")
                    .build()
            }
        }else {
            println("No image file found")
        }
        return Response.status(Response.Status.BAD_REQUEST).build()
    }

    private fun uploadImageToStorage(file: InputStream, fileName: String): String {
        val bucketName = "receipt-scanner-bucket"
        val blobId = BlobId.of(bucketName, "receipts/$fileName")
        val blobInfo = BlobInfo.newBuilder(blobId).build()

        // InputStreamを直接アップロード
        storage.create(blobInfo, file)

        return "https://storage.googleapis.com/$bucketName/receipts/$fileName"
    }

    private fun saveReceiptMetadataToFirestore(data: Map<String, Any>) {
        val firestore = com.google.cloud.firestore.FirestoreOptions.getDefaultInstance().service
        val collectionRef = firestore.collection("receipts")

        try {
            collectionRef.add(data).get()  // この呼び出しが非同期で、レスポンスを待機します
        } catch (e: Exception) {
            // Firestoreへの保存が失敗した場合
            throw RuntimeException("Failed to save data to Firestore: ${e.message}")
        }
    }
}
