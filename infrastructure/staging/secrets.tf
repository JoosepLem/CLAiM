resource "random_password" "db_password" {
  length  = 32
  special = false
}

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "app" {
  name        = "${var.project}/${var.environment}/app"
  description = "Database credentials and JWT secret for ${var.environment}"

  tags = {
    Project     = var.project
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    DB_NAME     = var.rds_db_name
    DB_USER     = var.rds_db_user
    DB_PASSWORD = random_password.db_password.result
    DB_HOST     = aws_db_instance.main.address
    DB_PORT     = "5432"
    JWT_SECRET  = random_password.jwt_secret.result
  })
}
