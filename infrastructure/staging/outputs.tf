output "alb_dns_name" {
  description = "ALB DNS name — create a CNAME record at your DNS provider pointing test.claimai.ee to this"
  value       = aws_lb.app.dns_name
}

output "app_url" {
  description = "App URL once DNS and SSL are configured"
  value       = "https://${var.domain_name}"
}

output "acm_validation_records" {
  description = "DNS validation records for ACM certificate — add these at your DNS provider to validate SSL"
  value = {
    for dvo in aws_acm_certificate.app.domain_validation_options :
    dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  }
}

output "ecr_repository_url" {
  description = "ECR repository URL — docker push to this"
  value       = aws_ecr_repository.app.repository_url
}

output "rds_endpoint" {
  description = "RDS endpoint — connect with a SQL client"
  value       = aws_db_instance.main.address
}

output "rds_port" {
  description = "RDS port"
  value       = aws_db_instance.main.port
}

output "rds_db_name" {
  description = "Database name"
  value       = var.rds_db_name
}

output "kms_key_arn" {
  description = "ARN of the KMS encryption key"
  value       = aws_kms_key.encryption.arn
}

output "kms_key_id" {
  description = "KMS key ID for app configuration"
  value       = aws_kms_key.encryption.id
}

output "secrets_manager_arn" {
  description = "ARN of the Secrets Manager secret"
  value       = aws_secretsmanager_secret.app.arn
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service name"
  value       = aws_ecs_service.app.name
}
