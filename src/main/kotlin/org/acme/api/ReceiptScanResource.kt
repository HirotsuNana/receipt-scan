package org.acme.api

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
import java.io.ByteArrayInputStream
import java.io.InputStream

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

            receiptService.processReceipt(ByteArrayInputStream(imageBytes))

            Response.ok("Receipt processed successfully").build()
        } catch (e: IllegalArgumentException) {
            Log.error("Invalid data: ${e.message}")
            createBadRequestResponse("Invalid data: ${e.message}")
        } catch (e: Exception) {
            Log.error("Error during receipt scanning: ${e.message}")
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Failed to process image: ${e.message}")
                .build()
        }
    }

    private fun createBadRequestResponse(message: String): Response {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(message)
            .build()
    }
}
