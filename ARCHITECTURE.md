# Task Manager API — Architecture Document

## Overview

This project is an educational Spring Boot REST API demonstrating industry-standard patterns for building, testing, and deploying Java microservices. It manages "tasks" (a simple CRUD domain) but is designed to serve as a **reusable template** for real applications.

Every design decision prioritises:
- **Clarity** — patterns are explicit and annotated
- **Extensibility** — OAuth2, Docker, Kubernetes, and Terraform are pre-planned
- **Correctness** — layered separation of concerns, validated inputs, structured errors

---

## Technology Stack

| Layer | Technology | Why |
|---|---|---|
| Language | Java 17 (LTS) | Long-term support, records, text blocks, pattern matching |
| Framework | Spring Boot 3.3 | Industry standard, opinionated defaults, vast ecosystem |
| HTTP Server | Embedded Apache Tomcat 10 | Production-grade, zero install, included by `spring-boot-starter-web` |
| Build Tool | Maven 3.9 | Declarative, reproducible builds; `pom.xml` is the single source of truth |
| Persistence | Spring Data JPA + Hibernate | Repository abstraction eliminates boilerplate SQL |
| Database (dev) | H2 in-memory | Zero install, clean state per run |
| Database (prod) | PostgreSQL | Robust, open-source, battle-tested |
| Validation | Jakarta Bean Validation | Declarative constraints on DTOs, validated automatically by Spring MVC |
| Mapping | MapStruct | Compile-time DTO↔entity mapping, no runtime reflection |
| API Docs | SpringDoc / Swagger UI | Auto-generated from annotations, interactive browser UI |
| Security | Spring Security + OAuth2 stub | Ready to plug in any OAuth2 provider |
| Observability | Spring Boot Actuator | Health checks, metrics, live configuration inspection |
| Testing | JUnit 5 + Mockito + MockMvc | Unit tests (no Spring), slice tests (MVC only), integration tests (full) |
| Containers | Docker multi-stage | Minimal production image, non-root user |
| Orchestration | Kubernetes (NGINX Ingress) | Zero-downtime rolling deploys, health-based routing |
| Infrastructure | Terraform (AWS ECS Fargate) | Reproducible, version-controlled infrastructure |

---

## Architecture Diagram

```
                           ┌──────────────────────────────────────────────────────────────┐
                           │                    SPRING BOOT APPLICATION                    │
                           │                                                                │
  HTTP Request             │  ┌─────────────────────────────────────────────────────────┐  │
─────────────────────────► │  │                SECURITY FILTER CHAIN                    │  │
  POST /api/v1/tasks       │  │  (Spring Security — validates Auth header, sets context) │  │
  GET  /api/v1/tasks/1     │  └──────────────────────────┬──────────────────────────────┘  │
  PUT  /api/v1/tasks/1     │                             │                                  │
  DELETE /api/v1/tasks/1   │                             ▼                                  │
                           │  ┌─────────────────────────────────────────────────────────┐  │
                           │  │              DISPATCHER SERVLET (Spring MVC)             │  │
                           │  │  Routes request to the correct @RestController method    │  │
                           │  └──────────────────────────┬──────────────────────────────┘  │
                           │                             │                                  │
                           │                             ▼                                  │
                           │  ┌─────────────────────────────────────────────────────────┐  │
                           │  │                  CONTROLLER LAYER                        │  │
                           │  │              TaskController                              │  │
                           │  │  • Deserialise JSON → DTO                               │  │
                           │  │  • Validate DTO (@Valid)                                │  │
                           │  │  • Delegate to service                                  │  │
                           │  │  • Build HTTP response (status, headers, body)           │  │
                           │  └──────────────────────────┬──────────────────────────────┘  │
                           │                             │ calls TaskService (interface)     │
                           │                             ▼                                  │
                           │  ┌─────────────────────────────────────────────────────────┐  │
                           │  │                  SERVICE LAYER                           │  │
                           │  │              TaskServiceImpl                             │  │
                           │  │  • Business rules (e.g., new tasks start as TODO)       │  │
                           │  │  • Transaction boundary (@Transactional)                │  │
                           │  │  • Orchestrates repository + mapper                     │  │
                           │  │  • Throws domain exceptions (TaskNotFoundException)     │  │
                           │  └──────────┬──────────────────────────┬───────────────────┘  │
                           │             │ calls                     │ calls                 │
                           │             ▼                           ▼                       │
                           │  ┌────────────────────┐   ┌────────────────────────────────┐  │
                           │  │  REPOSITORY LAYER  │   │       MAPPER LAYER             │  │
                           │  │  TaskRepository    │   │       TaskMapper               │  │
                           │  │  Spring Data JPA   │   │       (MapStruct-generated)    │  │
                           │  │  Generates SQL     │   │  Entity ↔ DTO conversion       │  │
                           │  └────────┬───────────┘   └────────────────────────────────┘  │
                           │           │                                                     │
                           │           ▼                                                     │
                           │  ┌─────────────────────────────────────────────────────────┐  │
                           │  │                  PERSISTENCE LAYER                       │  │
                           │  │       Hibernate ORM + HikariCP Connection Pool           │  │
                           │  └──────────────────────────┬──────────────────────────────┘  │
                           └─────────────────────────────┼────────────────────────────────┘
                                                         │ JDBC
                                                         ▼
                                              ┌─────────────────────┐
                                              │   DATABASE           │
                                              │  H2 (dev)           │
                                              │  PostgreSQL (prod)   │
                                              └─────────────────────┘

  Exception Path:
  Any layer throws → GlobalExceptionHandler (@RestControllerAdvice) → structured JSON error response
```

---

## Package Structure

```
com.example.taskmanager/
│
├── TaskManagerApplication.java     ← Entry point. @SpringBootApplication starts everything.
│
├── config/
│   ├── SecurityConfig.java         ← Security rules. OAuth2 extension point.
│   └── OpenApiConfig.java          ← Swagger UI metadata. Bearer auth stub.
│
├── controller/
│   └── TaskController.java         ← HTTP layer. Routes, parses, responds. No business logic.
│
├── service/
│   ├── TaskService.java            ← Interface. Controllers depend on this abstraction.
│   └── TaskServiceImpl.java        ← Implementation. All business logic lives here.
│
├── repository/
│   └── TaskRepository.java         ← Data access. Spring Data generates the implementation.
│
├── entity/
│   ├── Task.java                   ← JPA entity. Maps to the "tasks" database table.
│   └── TaskStatus.java             ← Enum: TODO | IN_PROGRESS | DONE
│
├── dto/
│   ├── TaskCreateRequest.java      ← What clients send for POST. Validated, no ID/timestamps.
│   ├── TaskUpdateRequest.java      ← What clients send for PUT. Includes status.
│   └── TaskResponse.java           ← What clients receive. Full task state.
│
├── mapper/
│   └── TaskMapper.java             ← MapStruct interface. Entity ↔ DTO conversions.
│
└── exception/
    ├── TaskNotFoundException.java   ← Domain exception for missing tasks → 404.
    ├── ErrorResponse.java           ← Uniform JSON error structure.
    └── GlobalExceptionHandler.java  ← Catches exceptions from all controllers → error responses.
```

---

## The DTO Pattern

This is one of the most important patterns in the codebase:

```
[Client JSON] ──deserialise──► [TaskCreateRequest DTO] ──map──► [Task Entity] ──save──► [Database]
[Database]    ──load──► [Task Entity] ──map──► [TaskResponse DTO] ──serialise──► [Client JSON]
```

**Why not expose the entity directly?**

| Problem | Example |
|---|---|
| Security | Client could set `id` and overwrite another user's task |
| Coupling | Renaming a database column breaks the API contract |
| Laziness | JPA lazy-loaded associations cause `LazyInitializationException` when serialised |
| Validation mismatch | Entity constraints (DB level) differ from API constraints (HTTP level) |

**Rule:** Entities never cross the controller boundary. DTOs never reach the repository.

---

## Exception Handling Flow

```
TaskServiceImpl.getTaskById(99)
  → repository.findById(99) returns Optional.empty()
  → throws TaskNotFoundException("Task not found with id: 99")
  → propagates up through TaskController.getTaskById()
  → caught by GlobalExceptionHandler.handleTaskNotFound()
  → returns HTTP 404 with body:
    {
      "status": 404,
      "error": "Not Found",
      "message": "Task not found with id: 99",
      "path": "/api/v1/tasks/99",
      "timestamp": "2024-01-15T10:30:00",
      "fieldErrors": null
    }
```

The `GlobalExceptionHandler` ensures that:
- All errors have the same JSON structure
- Stack traces are never exposed to clients
- HTTP status codes are semantically correct (404 vs 400 vs 500)

---

## Security Extension: Adding OAuth2

The application is pre-structured for OAuth2. To enable it:

**Step 1 — Add the dependency in `pom.xml`:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

**Step 2 — Configure the identity provider in `application.yml`:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-idp.example.com/realms/myrealm
```
Spring fetches the public signing keys from `{issuer-uri}/.well-known/openid-configuration` and validates every incoming Bearer token automatically.

**Step 3 — Update `SecurityConfig.java`:**
```java
// Replace httpBasic with:
.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
// Change permitAll to:
.anyRequest().authenticated()
```

**Step 4 — Enable method-level security (optional):**
Uncomment `@EnableMethodSecurity` in `SecurityConfig` and add `@PreAuthorize` to service methods:
```java
@PreAuthorize("hasAuthority('SCOPE_tasks:write')")
public TaskResponse createTask(TaskCreateRequest request) { ... }
```

**Supported Providers:** Keycloak, Auth0, Okta, AWS Cognito, Google, Azure AD — any provider that issues RS256 JWT tokens.

---

## Running the Application

### Prerequisites
- Java 21 ([Adoptium](https://adoptium.net))
- Maven 3.9 (or use the `./mvnw` wrapper included by Spring Initializr)

### Start Locally
```bash
mvn spring-boot:run
```
The app starts on `http://localhost:8080`.

### Explore the API
| URL | Purpose |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive API documentation |
| `http://localhost:8080/h2-console` | H2 database browser (JDBC URL: `jdbc:h2:mem:taskdb`) |
| `http://localhost:8080/actuator/health` | Health check |

### Sample curl Commands
```bash
# Create a task
curl -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"title": "Learn Spring Boot", "description": "Complete this tutorial"}'

# Get task by ID
curl http://localhost:8080/api/v1/tasks/1

# List all tasks (paginated)
curl "http://localhost:8080/api/v1/tasks?page=0&size=10&sort=createdAt,desc"

# Filter by status
curl "http://localhost:8080/api/v1/tasks?status=TODO"

# Update a task
curl -X PUT http://localhost:8080/api/v1/tasks/1 \
  -H "Content-Type: application/json" \
  -d '{"title": "Learn Spring Boot", "description": "Done!", "status": "DONE"}'

# Delete a task
curl -X DELETE http://localhost:8080/api/v1/tasks/1
```

---

## Running Tests

```bash
# Run all tests
mvn test

# Run only unit tests (fast — no Spring context)
mvn test -Dtest=TaskServiceImplTest

# Run only controller slice tests
mvn test -Dtest=TaskControllerTest

# Run with coverage report (add jacoco plugin to pom.xml)
mvn verify
```

### Test Hierarchy

| Test Class | Annotation | What Starts | Speed |
|---|---|---|---|
| `TaskManagerApplicationTests` | `@SpringBootTest` | Full context + Tomcat + H2 | Slow (~5s) |
| `TaskControllerTest` | `@WebMvcTest` | MVC layer only, no DB | Fast (~1s) |
| `TaskServiceImplTest` | `@ExtendWith(MockitoExtension.class)` | Nothing (pure JUnit) | Very fast (<100ms) |

---

## Running with Docker

```bash
# Build the image
docker build -t task-manager:latest .

# Run the container
docker run -p 8080:8080 task-manager:latest

# Using Docker Compose (includes health check)
docker-compose up --build

# Run in background
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop
docker-compose down
```

---

## Deploying to Kubernetes

### Prerequisites
- `kubectl` configured for your cluster
- Ingress controller installed (nginx): `kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml`
- Image pushed to a registry accessible by the cluster

### Deploy All Resources
```bash
kubectl apply -f k8s/

# Watch rollout
kubectl rollout status deployment/task-manager

# Check pods
kubectl get pods -l app=task-manager

# Check service
kubectl get svc task-manager-svc

# Check ingress
kubectl get ingress task-manager-ingress

# View logs
kubectl logs -l app=task-manager --tail=100 -f

# Port-forward for local testing (bypasses ingress)
kubectl port-forward svc/task-manager-svc 8080:80
curl http://localhost:8080/actuator/health
```

### Update the Deployment (Rolling Update)
```bash
# After pushing a new image:
kubectl set image deployment/task-manager task-manager=task-manager:1.1.0

# Or edit the deployment YAML and re-apply:
kubectl apply -f k8s/deployment.yaml

# Rollback if something went wrong:
kubectl rollout undo deployment/task-manager
```

---

## Deploying with Terraform (AWS ECS Fargate)

### Prerequisites
- AWS CLI configured (`aws configure`)
- Terraform >= 1.6 installed ([terraform.io](https://www.terraform.io))
- Docker image pushed to ECR

### Deploy
```bash
cd terraform

# Download providers
terraform init

# Preview changes (ALWAYS do this first)
terraform plan -var="container_image=123456789.dkr.ecr.us-east-1.amazonaws.com/task-manager:1.0.0"

# Apply (creates AWS resources — this costs money)
terraform apply -var="container_image=123456789.dkr.ecr.us-east-1.amazonaws.com/task-manager:1.0.0"

# View outputs
terraform output

# Tear down everything
terraform destroy
```

### Recommended Variable File Pattern
```hcl
# terraform/prod.tfvars (gitignored)
environment      = "prod"
container_image  = "123456789.dkr.ecr.us-east-1.amazonaws.com/task-manager:1.0.0"
desired_count    = 4
cpu              = 1024
memory           = 2048
log_retention_days = 30
```
```bash
terraform apply -var-file=prod.tfvars
```

---

## API Reference

### Base URL
`http://localhost:8080/api/v1`

### Endpoints

| Method | Path | Description | Status Codes |
|---|---|---|---|
| `POST` | `/tasks` | Create a task | 201, 400 |
| `GET` | `/tasks` | List tasks (paginated) | 200 |
| `GET` | `/tasks?status=TODO` | Filter by status | 200, 400 |
| `GET` | `/tasks/{id}` | Get task by ID | 200, 404 |
| `PUT` | `/tasks/{id}` | Replace a task | 200, 400, 404 |
| `DELETE` | `/tasks/{id}` | Delete a task | 204, 404 |

### Request Bodies

**POST /tasks**
```json
{
  "title": "Write unit tests",
  "description": "Cover the service layer with Mockito"
}
```

**PUT /tasks/{id}**
```json
{
  "title": "Write unit tests",
  "description": "Done!",
  "status": "DONE"
}
```

### Response Body (Task)
```json
{
  "id": 1,
  "title": "Write unit tests",
  "description": "Cover the service layer with Mockito",
  "status": "TODO",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00"
}
```

### Error Response Body
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for 1 field(s). See 'fieldErrors' for details.",
  "path": "/api/v1/tasks",
  "timestamp": "2024-01-15T10:30:00",
  "fieldErrors": {
    "title": "Title is required and must not be blank"
  }
}
```

### Task Status Values
| Value | Description |
|---|---|
| `TODO` | Created, no work started (default on creation) |
| `IN_PROGRESS` | Work is underway |
| `DONE` | Completed |

### Pagination Query Parameters
| Parameter | Default | Example |
|---|---|---|
| `page` | `0` | `?page=1` |
| `size` | `20` | `?size=5` |
| `sort` | `createdAt,desc` | `?sort=title,asc` |

---

## Production Readiness Checklist

Before going to production, add:

- [ ] **OAuth2**: Uncomment `oauth2ResourceServer` in `SecurityConfig.java`
- [ ] **Database**: Switch to PostgreSQL profile with managed RDS
- [ ] **Schema migrations**: Add Flyway and SQL migration scripts
- [ ] **HTTPS**: TLS via cert-manager (K8s) or ACM + ALB (AWS)
- [ ] **Structured logging**: Replace `System.err` with SLF4J + JSON format (Logback)
- [ ] **Distributed tracing**: Add Micrometer Tracing + Zipkin/Jaeger
- [ ] **Rate limiting**: nginx Ingress annotations or API Gateway
- [ ] **Secrets management**: AWS Secrets Manager / Vault (no plaintext passwords)
- [ ] **Health probes**: Confirm `management.endpoint.health.probes.enabled=true`
- [ ] **Resource limits**: Tune K8s requests/limits based on load testing
- [ ] **HPA**: Add HorizontalPodAutoscaler for CPU-based scaling
- [ ] **Network policies**: Restrict pod-to-pod traffic in Kubernetes
- [ ] **Remote Terraform state**: S3 backend + DynamoDB locking
- [ ] **CI/CD pipeline**: Build → Test → Dockerise → Push to ECR → Deploy
