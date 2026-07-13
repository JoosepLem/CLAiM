package ee.claimai.encryption

import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.EncryptRequest
import software.amazon.awssdk.services.kms.model.DecryptRequest

class AwsKmsOperations(
    private val kmsClient: KmsClient,
    private val keyId: String
) : KmsOperations {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val start = System.currentTimeMillis()
        val response = kmsClient.encrypt { req ->
            req.keyId(keyId)
            req.plaintext(SdkBytes.fromByteArray(plaintext))
        }
        log.info("aws_kms encrypt keyId={} duration_ms={}", keyId, System.currentTimeMillis() - start)
        return response.ciphertextBlob().asByteArray()
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val start = System.currentTimeMillis()
        val response = kmsClient.decrypt { req ->
            req.ciphertextBlob(SdkBytes.fromByteArray(ciphertext))
        }
        log.info("aws_kms decrypt keyId={} duration_ms={}", keyId, System.currentTimeMillis() - start)
        return response.plaintext().asByteArray()
    }

    companion object {
        private val log = LoggerFactory.getLogger(AwsKmsOperations::class.java)
    }
}
