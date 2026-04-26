# terraform/main.tf — AWS ECS Fargate Deployment
# =================================================
# This file provisions the AWS infrastructure needed to run the task-manager
# container on ECS (Elastic Container Service) with the Fargate launch type.
#
# FARGATE vs EC2 LAUNCH TYPE:
#   Fargate — serverless: AWS manages the underlying servers. You pay per task
#             (vCPU + memory-seconds). No patching of EC2 instances.
#   EC2     — you manage the EC2 cluster nodes. More control, more responsibility,
#             often cheaper at scale. Use for large, stable workloads.
#
# WHAT THIS CREATES:
#   - ECS Cluster (logical grouping of tasks)
#   - ECS Task Definition (container spec: image, CPU, memory, env vars)
#   - ECS Service (runs N copies of the task, handles placement, rolling updates)
#   - CloudWatch Log Group (captures container stdout/stderr)
#   - IAM Role (allows ECS to pull from ECR and write logs)
#
# WHAT THIS DOES NOT CREATE (left as extension exercises):
#   - VPC / subnets (uses default VPC for simplicity)
#   - Application Load Balancer (for HTTPS termination and path-based routing)
#   - ACM Certificate (for TLS)
#   - RDS PostgreSQL instance (for the database)
#   - Security Groups (commented stubs provided)
#
# TERRAFORM FLOW:
#   terraform init     → download AWS provider (~100MB)
#   terraform plan     → preview changes (always do this before apply)
#   terraform apply    → create/update infrastructure
#   terraform destroy  → delete all managed resources

# ─────────────────────────────────────────────────────────────────────────────
# REQUIRED VERSION CONSTRAINTS
# ─────────────────────────────────────────────────────────────────────────────
terraform {
  required_version = ">= 1.6.0"
  # Pin the minimum Terraform version. Prevents running with an older version
  # that might not support syntax used in this file.

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
      # ~> 5.0 means: >= 5.0 and < 6.0 (accept patch and minor updates, not major)
      # Pin provider versions to avoid breaking changes from provider upgrades.
    }
  }

  # ── Remote State Backend (uncomment for team environments) ───────────────
  # Terraform state tracks what infrastructure exists so it can plan changes.
  # By default, state is stored in terraform.tfstate (local file).
  # For teams: store state remotely so everyone sees the same state.
  #
  # backend "s3" {
  #   bucket         = "your-org-terraform-state"  # S3 bucket for state storage
  #   key            = "task-manager/terraform.tfstate"  # Path within the bucket
  #   region         = "us-east-1"
  #   dynamodb_table = "terraform-state-lock"  # Prevents concurrent applies
  #   encrypt        = true                    # Encrypt state at rest
  # }
}

# ─────────────────────────────────────────────────────────────────────────────
# PROVIDER CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────
# The AWS provider authenticates using the standard AWS credential chain:
#   1. Environment variables: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
#   2. AWS credentials file: ~/.aws/credentials
#   3. IAM instance profile (for EC2 or ECS tasks)
#   4. IAM role for CI/CD (GitHub Actions OIDC, CircleCI OIDC)
#
# Best practice for CI: Use OIDC federation (no long-lived credentials).
provider "aws" {
  region = var.aws_region

  # Optional: tag ALL resources created by this provider with common tags.
  # This is critical for cost allocation and resource cleanup.
  default_tags {
    tags = {
      Application = var.app_name
      Environment = var.environment
      ManagedBy   = "terraform"
      # Team        = "backend"
    }
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# NETWORKING — Discover Default VPC and Subnets
# ─────────────────────────────────────────────────────────────────────────────
# "data sources" read existing infrastructure (not managed by this config).
# We read the default VPC rather than creating a new one for simplicity.
# PRODUCTION: Create a proper VPC with private subnets for the ECS tasks and
# a public subnet only for the load balancer.

data "aws_vpc" "target" {
  # If var.vpc_id is provided, look up that VPC. Otherwise, use the default VPC.
  id      = var.vpc_id != "" ? var.vpc_id : null
  default = var.vpc_id == "" ? true : null
}

data "aws_subnets" "available" {
  # Get all subnets in the target VPC.
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.target.id]
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# CLOUDWATCH LOG GROUP
# ─────────────────────────────────────────────────────────────────────────────
# ECS containers write stdout/stderr here. View logs with:
#   aws logs tail /ecs/task-manager-dev --follow
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.app_name}-${var.environment}"
  retention_in_days = var.log_retention_days

  # tags inherited from provider default_tags
}

# ─────────────────────────────────────────────────────────────────────────────
# IAM — ECS Task Execution Role
# ─────────────────────────────────────────────────────────────────────────────
# ECS needs an IAM role to:
#   1. Pull the container image from ECR (Elastic Container Registry)
#   2. Write logs to CloudWatch
# This is the "execution role" — it runs before the container starts.

data "aws_iam_policy_document" "ecs_assume_role" {
  # Trust policy: allow the ECS task service to assume this role
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_execution_role" {
  name               = "${var.app_name}-${var.environment}-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
}

resource "aws_iam_role_policy_attachment" "ecs_execution_role_policy" {
  role       = aws_iam_role.ecs_execution_role.name
  # AWS-managed policy that grants ECR pull and CloudWatch log write permissions
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# ── Task Role (optional — for the APPLICATION to call AWS services) ─────────
# The task role gives the RUNNING container permissions to call AWS APIs.
# Example: if your app reads from S3 or sends to SQS.
# resource "aws_iam_role" "ecs_task_role" {
#   name               = "${var.app_name}-${var.environment}-task-role"
#   assume_role_policy = data.aws_iam_policy_document.ecs_assume_role.json
# }

# ─────────────────────────────────────────────────────────────────────────────
# ECS CLUSTER
# ─────────────────────────────────────────────────────────────────────────────
# An ECS cluster is a logical grouping of services and tasks.
# With Fargate, there are no EC2 instances to manage — just the cluster object.
resource "aws_ecs_cluster" "main" {
  name = "${var.app_name}-${var.environment}"

  # Enable Container Insights for enhanced CloudWatch metrics (CPU, memory, network)
  # Adds cost but provides much better observability in production.
  setting {
    name  = "containerInsights"
    value = var.environment == "prod" ? "enabled" : "disabled"
    # Enable in prod, disable in dev to save cost
  }
}

# ─────────────────────────────────────────────────────────────────────────────
# ECS TASK DEFINITION
# ─────────────────────────────────────────────────────────────────────────────
# A Task Definition is the "blueprint" for a container. It specifies:
# the image, CPU/memory, port mappings, environment variables, logging config.
# Updating a task definition creates a new REVISION (immutable versions).
resource "aws_ecs_task_definition" "app" {
  family                   = "${var.app_name}-${var.environment}"
  # awsvpc: each task gets its own elastic network interface (ENI) with its own
  # IP address. Required for Fargate. Enables security groups at the task level.
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  # task_role_arn          = aws_iam_role.ecs_task_role.arn  # Uncomment if using task role

  # container_definitions is a JSON string describing one or more containers.
  # jsonencode() converts a Terraform map/list to JSON.
  container_definitions = jsonencode([
    {
      name      = var.app_name
      image     = var.container_image
      essential = true  # If this container stops, the whole task stops

      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
          # hostPort is omitted for awsvpc mode (automatically equals containerPort)
        }
      ]

      # Environment variables injected at runtime.
      # Non-sensitive config here; sensitive config should use 'secrets' below.
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "postgres" },
        { name = "SERVER_PORT",            value = tostring(var.container_port) },
        { name = "DB_HOST",                value = "your-rds-endpoint.rds.amazonaws.com" },
        { name = "DB_PORT",                value = "5432" },
        { name = "DB_NAME",                value = "taskdb" },
        { name = "DB_USER",                value = "postgres" },
      ]

      # ── Secrets from AWS Secrets Manager ──────────────────────────────
      # Values are injected as env vars but fetched from Secrets Manager at
      # container start — never stored in the task definition or CloudWatch logs.
      # secrets = [
      #   {
      #     name      = "DB_PASSWORD"
      #     valueFrom = "arn:aws:secretsmanager:us-east-1:123456789:secret:task-manager/db-password"
      #   }
      # ]

      # ── Health Check ────────────────────────────────────────────────────
      healthCheck = {
        command     = ["CMD-SHELL", "wget -q -O /dev/null http://localhost:${var.container_port}/actuator/health || exit 1"]
        interval    = 30
        timeout     = 10
        retries     = 3
        startPeriod = 60  # Grace period for JVM startup
      }

      # ── Logging (CloudWatch) ─────────────────────────────────────────────
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
          # Each container instance gets its own log stream:
          # /ecs/task-manager-dev/ecs/task-manager/{task-id}
        }
      }
    }
  ])
}

# ─────────────────────────────────────────────────────────────────────────────
# ECS SERVICE
# ─────────────────────────────────────────────────────────────────────────────
# An ECS Service ensures that the desired number of task instances are always
# running. It handles task placement, rolling deployments, and health monitoring.
resource "aws_ecs_service" "app" {
  name            = var.app_name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  # DEPLOYMENT CONFIGURATION — controls rolling update behaviour
  deployment_minimum_healthy_percent = 50   # Allow bringing half tasks down during deployment
  deployment_maximum_percent         = 200  # Allow double the tasks during deployment

  network_configuration {
    # assign_public_ip = true: Each task gets a public IP (needed without NAT Gateway).
    # PRODUCTION: use private subnets with a NAT Gateway. Tasks should NOT have
    # public IPs — they receive traffic through the load balancer only.
    subnets          = data.aws_subnets.available.ids
    assign_public_ip = true  # Set false for private subnets

    # Security groups — uncomment when you create them:
    # security_groups = [aws_security_group.ecs_tasks.id]
  }

  # ── Load Balancer Integration ──────────────────────────────────────────────
  # Uncomment when an Application Load Balancer is provisioned.
  # The ALB target group registers and deregisters tasks automatically.
  # load_balancer {
  #   target_group_arn = aws_lb_target_group.app.arn
  #   container_name   = var.app_name
  #   container_port   = var.container_port
  # }

  # Force a new deployment when the task definition changes.
  # Without this, updating task_definition arn alone might not trigger redeployment.
  force_new_deployment = true

  lifecycle {
    # Ignore desired_count changes — let auto-scaling manage it after initial deploy
    ignore_changes = [desired_count]
  }

  depends_on = [
    aws_iam_role_policy_attachment.ecs_execution_role_policy
    # aws_lb_listener.http   # Uncomment when load balancer is added
  ]
}

# ─────────────────────────────────────────────────────────────────────────────
# AUTO-SCALING (stub — uncomment to enable)
# ─────────────────────────────────────────────────────────────────────────────
# Scale the ECS service based on CPU utilization.
#
# resource "aws_appautoscaling_target" "ecs" {
#   max_capacity       = 10
#   min_capacity       = var.desired_count
#   resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.app.name}"
#   scalable_dimension = "ecs:service:DesiredCount"
#   service_namespace  = "ecs"
# }
#
# resource "aws_appautoscaling_policy" "cpu" {
#   name               = "${var.app_name}-cpu-scaling"
#   policy_type        = "TargetTrackingScaling"
#   resource_id        = aws_appautoscaling_target.ecs.resource_id
#   scalable_dimension = aws_appautoscaling_target.ecs.scalable_dimension
#   service_namespace  = aws_appautoscaling_target.ecs.service_namespace
#
#   target_tracking_scaling_policy_configuration {
#     target_value = 70.0  # Scale out when average CPU > 70%
#     predefined_metric_specification {
#       predefined_metric_type = "ECSServiceAverageCPUUtilization"
#     }
#   }
# }
