resource "aws_acm_certificate" "app" {
  domain_name       = var.domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name        = "${var.project}-${var.environment}-cert"
    Project     = var.project
    Environment = var.environment
  }
}
