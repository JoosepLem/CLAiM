output "state_bucket" {
  description = "S3 bucket for Terraform state — use this in staging/backend.tf"
  value       = aws_s3_bucket.terraform_state.bucket
}

output "lock_table" {
  description = "DynamoDB table for Terraform state locking"
  value       = aws_dynamodb_table.terraform_lock.name
}
