resource "aws_kms_key" "encryption" {
  description             = "CLAiM envelope encryption master key for ${var.environment}"
  enable_key_rotation     = true
  rotation_period_in_days = 90

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "EnableIAMAdmin"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      },
      {
        Sid    = "AllowEcsTaskEncryptDecrypt"
        Effect = "Allow"
        Principal = {
          AWS = aws_iam_role.ecs_task.arn
        }
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:GenerateDataKey",
          "kms:GenerateDataKeyWithoutPlaintext",
          "kms:DescribeKey"
        ]
        Resource = "*"
      }
    ]
  })

  tags = {
    Name        = "${var.project}-${var.environment}-encryption-key"
    Project     = var.project
    Environment = var.environment
  }
}

resource "aws_kms_alias" "encryption" {
  name          = "alias/${var.project}-${var.environment}-encryption"
  target_key_id = aws_kms_key.encryption.id
}
