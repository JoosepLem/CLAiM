variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-north-1"
}

variable "project" {
  description = "Project name used for resource naming"
  type        = string
  default     = "claim"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "staging"
}

variable "my_ip" {
  description = "Your home/public IP address in CIDR notation (e.g. 1.2.3.4/32). Used to whitelist RDS access."
  type        = string
  sensitive   = true
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "rds_instance_class" {
  description = "RDS instance type"
  type        = string
  default     = "db.t3.micro"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 20
}

variable "rds_max_allocated_storage" {
  description = "RDS max auto-scaled storage in GB"
  type        = number
  default     = 100
}

variable "rds_backup_retention_days" {
  description = "RDS automated backup retention in days"
  type        = number
  default     = 1
}

variable "rds_db_name" {
  description = "Database name"
  type        = string
  default     = "claim"
}

variable "rds_db_user" {
  description = "Database master username"
  type        = string
  default     = "claim"
}

variable "ecs_cpu" {
  description = "ECS Fargate task CPU units"
  type        = number
  default     = 512
}

variable "ecs_memory" {
  description = "ECS Fargate task memory in MiB"
  type        = number
  default     = 1024
}

variable "ecs_desired_count" {
  description = "Number of ECS tasks to run"
  type        = number
  default     = 1
}

variable "app_port" {
  description = "Port the application listens on"
  type        = number
  default     = 8080
}

variable "domain_name" {
  description = "Domain name for the staging app (e.g. test.claimai.ee)"
  type        = string
}

variable "cloudwatch_log_retention_days" {
  description = "CloudWatch log group retention in days"
  type        = number
  default     = 7
}
