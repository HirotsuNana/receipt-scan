import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.util.concurrent.TimeUnit

@ApplicationScoped
class StorageService {

    @ConfigProperty(name = "quarkus.google.cloud.credentials-location")
    lateinit var credentialsPath: String

    private val storage: Storage by lazy {
        Log.info("Using credentials from: $credentialsPath")

        // 認証ファイルの存在確認
        val credentialsFile = File(credentialsPath)
        if (!credentialsFile.exists()) {
            Log.error("Credentials file not found at: $credentialsPath")
            throw IllegalStateException("Credentials file does not exist at the specified location.")
        }

        // Google Cloud Storageクライアントの作成
        StorageOptions.newBuilder()
            .setCredentials(ServiceAccountCredentials.fromStream(FileInputStream(credentialsPath)))
            .build()
            .service
    }

    fun generateSignedUrl(bucketName: String, objectName: String, expirationMinutes: Long = 120): URL {
        val blobInfo = BlobInfo.newBuilder(bucketName, objectName).build()
        return storage.signUrl(blobInfo, expirationMinutes, TimeUnit.MINUTES)
    }
}
