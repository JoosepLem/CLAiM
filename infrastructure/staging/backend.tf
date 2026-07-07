# Run `infrastructure/bootstrap/` first, then uncomment this block.
# Update the bucket name to match the output from bootstrap (it includes your account ID).
#
# terraform {
#   backend "s3" {
#     bucket         = "claim-staging-tfstate-<your-account-id>"
#     key            = "staging/terraform.tfstate"
#     region         = "eu-north-1"
#     dynamodb_table = "claim-staging-terraform-lock"
#     encrypt        = true
#   }
# }
