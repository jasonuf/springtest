# terraform/outputs.tf — Output Values
# =======================================
# Outputs expose values from the Terraform state so they can be:
#   1. Printed to the console after `terraform apply`
#   2. Referenced by other Terraform configurations (modules, remote state)
#   3. Read by scripts or CI/CD pipelines: terraform output -json | jq '.ecs_cluster_name.value'
#
# BEST PRACTICE: Mark sensitive values (passwords, ARNs with account IDs) with
# sensitive = true so Terraform redacts them from plan output.

output "ecs_cluster_name" {
  description = "Name of the ECS cluster. Use this to scope AWS CLI commands."
  value       = aws_ecs_cluster.main.name
  # Example: aws ecs list-services --cluster $(terraform output -raw ecs_cluster_name)
}

output "ecs_cluster_arn" {
  description = "ARN of the ECS cluster."
  value       = aws_ecs_cluster.main.arn
}

output "ecs_service_name" {
  description = "Name of the ECS service."
  value       = aws_ecs_service.app.name
}

output "task_definition_arn" {
  description = "Full ARN of the active task definition (includes revision number)."
  value       = aws_ecs_task_definition.app.arn
  # Example: arn:aws:ecs:us-east-1:123456789:task-definition/task-manager-dev:5
  # The :5 at the end is the revision. Each `terraform apply` that changes the
  # task definition creates a new revision.
}

output "cloudwatch_log_group" {
  description = "CloudWatch Log Group name for application logs."
  value       = aws_cloudwatch_log_group.app.name
  # Tail logs: aws logs tail $(terraform output -raw cloudwatch_log_group) --follow
}

output "aws_region" {
  description = "AWS region where resources are deployed."
  value       = var.aws_region
}

output "deploy_instructions" {
  description = "Quick reference for common deployment operations."
  value       = <<-EOT
    ── Quick Reference ──────────────────────────────────────────────────────
    Force new deployment (after pushing a new image):
      aws ecs update-service \
        --cluster ${aws_ecs_cluster.main.name} \
        --service ${aws_ecs_service.app.name} \
        --force-new-deployment \
        --region ${var.aws_region}

    Watch deployment progress:
      aws ecs wait services-stable \
        --cluster ${aws_ecs_cluster.main.name} \
        --services ${aws_ecs_service.app.name} \
        --region ${var.aws_region}

    Tail application logs:
      aws logs tail ${aws_cloudwatch_log_group.app.name} \
        --follow \
        --region ${var.aws_region}

    Scale up:
      aws ecs update-service \
        --cluster ${aws_ecs_cluster.main.name} \
        --service ${aws_ecs_service.app.name} \
        --desired-count 4 \
        --region ${var.aws_region}
    ─────────────────────────────────────────────────────────────────────────
  EOT
}
