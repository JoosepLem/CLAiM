resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-${var.environment}-db-subnet"
  subnet_ids = aws_subnet.public[*].id

  tags = {
    Name        = "${var.project}-${var.environment}-db-subnet"
    Project     = var.project
    Environment = var.environment
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project}-${var.environment}"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.rds_instance_class

  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  storage_encrypted     = true

  db_name  = var.rds_db_name
  username = var.rds_db_user
  password = random_password.db_password.result
  port     = 5432

  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.main.name
  publicly_accessible    = true

  backup_retention_period = var.rds_backup_retention_days
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  deletion_protection = true
  skip_final_snapshot = false
  final_snapshot_identifier = "${var.project}-${var.environment}-final"

  tags = {
    Name        = "${var.project}-${var.environment}-rds"
    Project     = var.project
    Environment = var.environment
  }
}
