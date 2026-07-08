terraform {
  backend "s3" {
    bucket  = "claim-staging-tfstate-257526643965"
    key     = "staging/terraform.tfstate"
    region  = "eu-north-1"
    encrypt = true
  }
}
