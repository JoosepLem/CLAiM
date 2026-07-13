package ee.claimai.encryption

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.encryption.kms")
class AwsKmsProperties {
    var keyId: String = ""
    var region: String = "eu-north-1"
}
