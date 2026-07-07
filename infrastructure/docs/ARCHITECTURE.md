# CLAiM Infrastructure Architecture — Staging

## Overview

Staging infrastructure for the CLAiM proof-of-concept application, deployed to **AWS eu-north-1 (Stockholm, Sweden)** and managed via Terraform. The stack runs the Spring Boot + Kotlin POC application on ECS Fargate with PostgreSQL on RDS, targeting ~$59/month.

## Architecture decisions

Each decision is recorded with its context, the alternatives considered, and the rationale.

---

### 1. Platform: AWS over GCP

**Context:** The application uses AWS KMS for envelope encryption and was designed with the AWS ecosystem in mind (ECS, RDS, KMS, Secrets Manager).

**Alternatives considered:**

| | AWS | GCP |
|---|---|---|
| Monthly cost (staging) | ~$59 | ~$42 |
| Compute | ECS Fargate $18 | Cloud Run $30 |
| Load balancer | ALB $25 | Included in Cloud Run |
| Database | RDS db.t3.micro $15 | Cloud SQL db-f1-micro $11 |
| Terraform rewrite | None | Full rewrite required |
| KMS integration | Native (already coded) | Requires SDK migration |

**Decision:** AWS. GCP is $17/month cheaper because Cloud Run bakes HTTPS/load balancing into its runtime, eliminating the ALB cost. However, the app already has AWS KMS SDK integration, and all Terraform configs are written for AWS. The switching cost (Terraform rewrite + KMS SDK migration) doesn't justify saving $17/month on a staging environment.

---

### 2. VPC: Public subnets only, 2 AZs

**Context:** The POC needs network isolation for ECS and RDS, but staging doesn't warrant a full production-grade private subnet layout with NAT Gateway ($32/month).

**Alternatives considered:**

**Option A — Public subnets only (chosen):**
- ECS and RDS both in public subnets
- RDS protected by security group (only ECS + developer IP allowed)
- No NAT Gateway needed
- Internet-facing access via Internet Gateway (free)

**Option B — Public + private subnets with NAT Gateway:**
- ECS and RDS in private subnets, ALB in public
- NAT Gateway ($32/month) lets private subnets pull from ECR
- Defense-in-depth: database has no public IP

**Decision:** Option A. The NAT Gateway adds $32/month for marginal security benefit in a staging environment. The RDS security group ruleset (ECS-only + developer IP whitelist) provides adequate protection. The cost savings are significant relative to the total bill.

**Two availability zones** are used because the ALB requires at least two subnets to provision correctly, even in staging. Both AZs are in Stockholm, Sweden, ensuring data stays within the EU.

---

### 3. DNS and HTTPS

**Context:** The app uses `Secure` + `HttpOnly` cookies for JWT auth, requiring HTTPS. The domain `claimai.ee` is registered externally (DNS not in Route53).

**Approach:**
- ACM certificate for `test.claimai.ee` with **DNS validation**
- Manual DNS validation: Terraform outputs the validation TXT records; user adds them at their DNS provider
- User creates a CNAME record: `test.claimai.ee` → ALB DNS name
- ALB HTTPS listener on port 443 (TLS 1.2-1.3)
- ALB HTTP listener on port 80 redirects to HTTPS (301)

**Why DNS validation over email validation:** DNS validation enables automatic ACM certificate renewal without re-confirmation. Email validation requires re-approval on each renewal cycle.

**Why not Route53:** The domain's DNS is managed elsewhere. Migrating NS records to Route53 is a broader change with downtime risk — deferred until/if needed.

---

### 4. Database: RDS PostgreSQL, Single-AZ

**Spec:** `db.t3.micro`, PostgreSQL 16, 20 GB gp3 storage (auto-scale to 100 GB), single-AZ.

**Rationale for Single-AZ:** Multi-AZ doubles the RDS cost ($15 → $30) for high-availability that staging doesn't need. If the AZ goes down, the app is unavailable until it recovers — acceptable for a POC testing environment.

**Deletion protection ON** despite being staging — prevents accidental `terraform destroy` from wiping data.

**Backup retention:** 1 day. Minimal for staging; longer retention would increase storage costs without meaningful benefit.

**Publicly accessible** because the RDS instance is in a public subnet (see VPC decision). Access is restricted by security group — ECS task + developer IP only.

---

### 5. Compute: ECS Fargate, 0.5 vCPU / 1 GB

**Spec:** Single Fargate task, 0.5 vCPU / 1 GB memory, desired count = 1.

**Rationale for Fargate over EC2:** No server patching, no capacity planning, and Fargate handles host-level security. The ~$18/month cost is acceptable for the operational simplicity.

**0.5 vCPU / 1 GB** is the sweet spot — 0.25 vCPU / 0.5 GB is the cheapest tier but may choke on JVM startup and reconciliation queries. The app produces a custom JRE via `jlink` (stripped, minimal), so 1 GB is sufficient.

**No auto-scaling** because staging has zero concurrent users during testing. Scaling policies add complexity with no value until production.

---

### 6. Container Registry: ECR

**Spec:** Single ECR repository, immutable tags, scan on push, lifecycle policy keeping last 10 images.

**Manual push for now:** The Terraform creates the ECR repo and outputs the push URL. The user runs `docker build && docker tag && docker push` from their machine. CI/CD (GitHub Actions) can be layered on later without Terraform changes — ECR repo and ECS service stay identical.

---

### 7. Secrets Management: AWS Secrets Manager

**Spec:** One secret (`claim/staging/app`) containing DB credentials and JWT signing key as a JSON object. Values are auto-generated by Terraform (`random_password`) on first apply.

**Secret contents:**
```json
{
  "DB_NAME": "claim",
  "DB_USER": "claim",
  "DB_PASSWORD": "<auto-generated>",
  "DB_HOST": "<rds-endpoint>",
  "DB_PORT": "5432",
  "JWT_SECRET": "<auto-generated>"
}
```

**ECS integration:** The task definition references each JSON key via `valueFrom`, injecting them as environment variables at container startup. No env var hardcoding in the task definition.

**Why Secrets Manager over SSM Parameter Store:** Secrets Manager provides JSON structure (one secret, many values), automatic rotation support (future), and the ECS task execution role integration is straightforward. SSM SecureString is free but requires multiple individual parameters.

---

### 8. Encryption: AWS KMS

**Spec:** Symmetric CMK with 90-day automatic rotation. Key policy grants:
- **Root IAM user:** Full admin (`kms:*`) — for manual key management via CLI/console
- **ECS task role:** Encrypt, Decrypt, GenerateDataKey, DescribeKey — the minimum the app needs for envelope encryption

**Rotation model:**
- **KMS master key rotation** (90 days): AWS rotates the backing key material automatically. Transparent to the app — KMS stores version history so old DEKs still decrypt.
- **Tenant DEK rotation**: Application-level logic, not an infrastructure concern. The app generates new DEKs, wraps with KMS, re-encrypts data, and bumps the `key_version` column. ECS task role permissions (`kms:Encrypt` + `kms:GenerateDataKey`) support this without changes.

---

### 9. Database Migrations: Flyway at startup

**Context:** Flyway runs on application startup, applying pending migrations before the web context loads.

**Decision:** Run Flyway inside the application container at startup. With `desired_count = 1`, there's no risk of concurrent migration execution. This avoids the complexity of a separate migration job or init container.

**Future consideration:** If the service ever scales to 2+ tasks, Flyway at startup becomes dangerous (race condition on `flyway_schema_history`). At that point, migrations should be moved to a one-off ECS task that runs before the service deployment.

---

### 10. Logging: CloudWatch Logs

**Spec:** Log group `/ecs/claim-staging/app` with 7-day retention. ECS task uses the `awslogs` driver to stream stdout/stderr.

**No alarms.** For staging, the developer checks logs manually via the AWS Console or CLI when debugging. Alarm thresholds for a single-user POC would generate noise, not signal.

---

### 11. Terraform State: S3 + DynamoDB

**Context:** Local state files are a single point of failure (laptop loss = lose ability to manage infrastructure) and contain plaintext secrets.

**Decision:** S3 bucket for state storage (KMS-encrypted, versioned, blocked public access) + DynamoDB table for distributed locking. Bucket name includes AWS account ID to guarantee global uniqueness.

**Bootstrap pattern:** A separate `infrastructure/bootstrap/` Terraform config creates the S3 bucket and DynamoDB table before the main staging config. The staging backend references these resources. This is a one-time setup — once the bootstrap is applied and the staging backend block is uncommented, state lives in S3 permanently.

---

### 12. Terraform file structure: Flat

**Context:** Single environment (staging), no need for reusable modules across environments yet.

**Decision:** Flat `.tf` files in `infrastructure/staging/`, split by resource domain:
```
alb.tf          cloudwatch.tf   ecs.tf        kms.tf        rds.tf
acm.tf          ecr.tf          iam.tf        outputs.tf    secrets.tf
backend.tf      providers.tf    security_groups.tf  variables.tf  vpc.tf
```

When production is added (`infrastructure/production/`), common modules (VPC, RDS, ECS) can be extracted into `infrastructure/modules/` if the two environments meaningfully diverge.

---

### 13. Flyway at startup (not separate job)

Already covered in decision 9.

---

## Monthly cost estimate

| Resource | Monthly |
|---|---|
| ECS Fargate (0.5 vCPU, 1 GB) | $18 |
| ALB (hourly + LCUs) | $25 |
| RDS (db.t3.micro, 20 GB gp3, PG 18) | $15 |
| Secrets Manager (1 secret) | $0.40 |
| KMS (1 key) | $1 |
| ECR (minimal storage) | ~$0 |
| CloudWatch (within free tier) | $0 |
| **Total** | **~$59** |

---

## Resource naming convention

All resources follow the pattern `{project}-{environment}-{resource}` where:
- `project` = `claim`
- `environment` = `staging`

Examples: `claim-staging-vpc`, `claim-staging-ecs-task-execution`, `claim-staging-rds-sg`.

Tags on all resources: `Project = claim`, `Environment = staging`.

---

## What this stack does NOT include (deferred)

- **CI/CD pipeline** — images are pushed manually; `terraform apply` runs from the developer's machine
- **Route53 hosted zone** — domain DNS is managed externally
- **Multi-AZ RDS** — deferred to production
- **NAT Gateway** — not needed with public subnets
- **Auto-scaling** — single task for staging
- **CloudWatch alarms** — manual log inspection only
- **WAF / Shield** — minimal threat surface for staging
- **Multi-environment modules** — extracted when `production/` is created
