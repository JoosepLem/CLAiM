package ee.claimai.encryption

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
class EncryptionConfig {

    @Bean
    @Profile("dev", "docker", "test")
    fun localKmsOperations(properties: LocalKekProperties): KmsOperations {
        val keyBytes = hexToBytes(properties.kekHex)
        require(keyBytes.size == 32) { "app.encryption.local.kek-hex must be 32 bytes (64 hex chars)" }
        return LocalKmsOperations(SecretKeySpec(keyBytes, "AES"))
    }

    @Bean
    @Profile("staging", "prod")
    fun awsKmsOperations(properties: AwsKmsProperties): KmsOperations {
        val kmsClient = KmsClient.builder()
            .region(Region.of(properties.region))
            .build()
        return AwsKmsOperations(kmsClient, properties.keyId)
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

@ConfigurationProperties("app.encryption.local")
class LocalKekProperties {
    var kekHex: String = "0000000000000000000000000000000000000000000000000000000000000000"
}
