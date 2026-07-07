resource "aws_ecs_cluster" "main" {
  name = "${var.project}-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Project     = var.project
    Environment = var.environment
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${var.project}-${var.environment}-app"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.ecs_cpu
  memory                   = var.ecs_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name  = "app"
      image = "${aws_ecr_repository.app.repository_url}:latest"
      portMappings = [
        {
          containerPort = var.app_port
          protocol      = "tcp"
        }
      ]
      environment = [
        { name = "DB_NAME", value = var.rds_db_name },
        { name = "DB_PORT", value = "5432" },
      ]
      secrets = [
        { name = "DB_USER", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_USER::" },
        { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_PASSWORD::" },
        { name = "DB_HOST", valueFrom = "${aws_secretsmanager_secret.app.arn}:DB_HOST::" },
        { name = "JWT_SECRET", valueFrom = "${aws_secretsmanager_secret.app.arn}:JWT_SECRET::" },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.app.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "app"
        }
      }
      # No container health check — distroless base image has no shell/curl.
      # The ALB target group health check on GET / handles health monitoring.
    }
  ])

  tags = {
    Project     = var.project
    Environment = var.environment
  }
}

resource "aws_ecs_service" "app" {
  name            = "${var.project}-${var.environment}-app"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.ecs_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = var.app_port
  }

  depends_on = [
    aws_lb_listener.https
  ]

  tags = {
    Project     = var.project
    Environment = var.environment
  }
}
