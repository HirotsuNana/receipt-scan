import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.cloud.firestore.Firestore
import com.google.firebase.cloud.FirestoreClient
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import java.io.FileInputStream
import com.google.auth.oauth2.GoogleCredentials
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
class FirestoreProducer {

    @Produces
    fun createFirestore(
        @ConfigProperty(name = "quarkus.google.cloud.credentials-location") credentialsLocation: String
    ): Firestore {
        // Firebase を初期化
        val serviceAccount = FileInputStream(credentialsLocation)

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()

        FirebaseApp.initializeApp(options)
        return FirestoreClient.getFirestore()
    }
}
