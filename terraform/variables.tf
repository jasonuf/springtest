# terraform/variables.tf — Input Variable Declarations
# ======================================================
# Variables make Terraform configurations reusable across environments.
# Instead of hardcoding values, you declare variables here and provide
# values via:
#   1. terraform.tfvars file (DON'T commit if it contains secrets)
#   2. .auto.tfvars file (auto-loaded)
#   3. -var flag: terraform apply -var="environment=prod"
#   4. -var-file flag: terraform apply -var-file=prod.tfvars
#   5. Environment variables: TF_VAR_environment=prod terraform apply
#
# WORKFLOW:
#   terraform init      — download providers and modules
#   terraform validate  — check configuration syntax
#   terraform plan      — preview what WILL change (safe, read-only)
#   terraform apply     — make the changes (requires confirmation)
#   terraform destroy   — tear down all resources

# ── AWS Configuration ─────────────────────────────────────────────────────

variable "aws_region" {
  description = "AWS region to deploy resources into."
  type        = string
  default     = "us-east-1"
  # Common choices: us-east-1 (N. Virginia), eu-west-1 (Ireland), ap-southeast-1 (Singapore)
}

# ── Application Configuration ─────────────────────────────────────────────

variable "app_name" {
  description = "Application name. Used as a prefix for all resource names."
  type        = string
  default     = "task-manager"
  # Naming convention: {app_name}-{environment} → task-manager-dev, task-manager-prod
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod). Used in resource names and tags."
  type        = string
  default     = "dev"

  # validation block ensures only valid values are accepted.
  # Terraform will error immediately (before any resources are touched) if an
  # invalid value is provided — catching mistakes early.
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

# ── Container Configuration ────────────────────────────────────────────────

variable "container_image" {
  description = <<-EOT
    Full URI of the container image to deploy.
    Format: {account_id}.dkr.ecr.{region}.amazonaws.com/{repo}:{tag}
    Example: 123456789.dkr.ecr.us-east-1.amazonaws.com/task-manager:1.0.0

    Build and push to ECR:
      aws ecr create-repository --repository-name task-manager
      docker build -t task-manager:1.0.0 .
      docker tag task-manager:1.0.0 {ecr_uri}/task-manager:1.0.0
      docker push {ecr_uri}/task-manager:1.0.0
  EOT
  type        = string
  # No default — this MUST be provided. Terraform will fail immediately if omitted.
}

variable "container_port" {
  description = "Port the container listens on (must match server.port in application.yml and containerPort in Dockerfile)."
  type        = number
  default     = 8080
}

# ── ECS Fargate Configuration ──────────────────────────────────────────────

variable "desired_count" {
  description = "Number of ECS task instances to run. Set to 2+ for high availability."
  type        = number
  default     = 2
}

variable "cpu" {
  description = <<-EOT
    CPU units for the ECS task. 1 vCPU = 1024 CPU units.
    Valid Fargate values: 256, 512, 1024, 2048, 4096
    Higher CPU = faster startup + lower cold-start latency for the JVM.
    256 is fine for development; use 512+ for production.
  EOT
  type        = number
  default     = 512
}

variable "memory" {
  description = <<-EOT
    Memory in MB for the ECS task.
    Valid Fargate values depend on cpu:
      256 CPU  → 512, 1024, 2048
      512 CPU  → 1024-4096 (in 1024 increments)
      1024 CPU → 2048-8192 (in 1024 increments)
    JVM recommendation: set MaxRAMPercentage=75 in Dockerfile and allocate enough
    memory for heap + metaspace + threads. For a small app: 512MB is minimum.
  EOT
  type        = number
  default     = 1024
}

# ── Networking ─────────────────────────────────────────────────────────────

variable "vpc_id" {
  description = <<-EOT
    VPC ID to deploy into. If empty, uses the default VPC.
    For production, create a dedicated VPC with private subnets.
    Using the default VPC is acceptable for development only.
  EOT
  type    = string
  default = ""  # Empty = use default VPC (resolved in main.tf)
}

variable "log_retention_days" {
  description = "Number of days to retain CloudWatch logs. 7 for dev, 30-90 for prod."
  type        = number
  default     = 7
}
