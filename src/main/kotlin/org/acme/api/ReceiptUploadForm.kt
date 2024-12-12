package org.acme.api

import org.jboss.resteasy.annotations.providers.multipart.PartType
import jakarta.ws.rs.FormParam
import java.io.InputStream

class ReceiptUploadForm {

    @FormParam("image")
    @PartType("application/octet-stream") // or "image/png" / "image/jpeg"
    lateinit var image: InputStream

    @FormParam("fileName")
    @PartType("text/plain")
    var fileName: String? = null
}
