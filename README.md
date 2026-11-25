# Shortlink Service (Java 21 + Redis + Docker Compose)

A containerized Java 21 service paired with Redis for caching and fast lookups, multi-stage Docker builds, host-side `cron` log monitoring, and a phased AWS deployment path.

---

## Table of Contents

1. [Overview & System Design](#overview--system-design)
2. [Repository Layout](#repository-layout)
3. [Environment & Configuration](#environment--configuration)
4. [Local Setup & Verification](#local-setup--verification)
5. [Automated Log Monitoring (`cron`)](#automated-log-monitoring-cron)
6. [Troubleshooting & Common Pitfalls](#troubleshooting--common-pitfalls)
7. [AWS Deployment Plan](#aws-deployment-plan)
8. [Pre-Deployment Checklist](#pre-deployment-checklist)

---

## Overview & System Design

### Architecture Diagram

```text
+-------------------------------------------------------------------+
|                        Host / WSL2 / EC2                          |
|                                                                   |
|  Client / curl                                                    |
|       │                                                           |
|       ▼  HTTP :8080                                               |
|  +-------------------------------------------------------------+  |
|  | shortlink-service (Java 21, non-root user appuser:1001)     |  |
|  |  - Exposes HTTP API & healthcheck (/api/v1/system/cache-stats) |  |
|  |  - Connects over shortlink-net bridge network               |  |
|  +------------------------------+------------------------------+  |
|                                 │                                 |
|                TCP :6379        │                                 |
|                                 ▼                                 |
|  +-------------------------------------------------------------+  |
|  | shortlink_redis (Redis 8 Alpine)                            |  |
|  |  - --maxmemory 256mb --maxmemory-policy allkeys-lru         |  |
|  |  - --appendonly yes -> /data (backed by redis_data volume)  |  |
|  +-------------------------------------------------------------+  |
|                                                                   |
|  cron (every 5m) -> scripts/check-logs.sh                         |
|       -> docker compose logs --since=5m app db                    |
|       -> scans error/warning keywords -> logs/error_logs_*.log    |
+-------------------------------------------------------------------+
```

### Design Choices & Trade-Offs

* **Multi-stage `Dockerfile`:**
  * **Builder stage (`maven:3.9-eclipse-temurin-21` pinned by SHA-256):** Resolves dependencies via `mvn dependency:go-offline -B` using a BuildKit cache mount (`/root/.m2`), then builds the JAR via `mvn package -DskipTests -B`.
  * **Runner stage (`eclipse-temurin:21-jre-alpine` pinned by SHA-256):** Runs only the JRE (no Maven or JDK build tools in the final image), creates non-root user/group `appuser:1001`, and defines a container `HEALTHCHECK` against `http://localhost:8080/api/v1/system/cache-stats`.
* **Redis as a cache / fast lookup layer:**
  * Configured with `--maxmemory 256mb` and `--maxmemory-policy allkeys-lru` so least-recently-used keys are evicted automatically once memory reaches 256 MB.
  * Configured with `--appendonly yes` and a named volume (`redis_data:/data`) so AOF data persists across container restarts and `docker compose down` / `docker compose up` cycles (unless `-v` is passed).
* **Phased storage & service growth:**
  * Keep the core Java service + Redis + `cron` log monitor working cleanly first.
  * Add a durable system-of-record database (such as PostgreSQL with a dedicated volume or Amazon RDS) only when persistent records/transactions are needed, and split services only when workload bottlenecks justify it.

---

## Repository Layout

```text
shortlink-service/
├── Dockerfile              # Multi-stage Maven build + non-root Alpine JRE runtime
├── compose.yml             # App & Redis services, bridge network, and named volume
├── .dockerignore           # Excludes target/, .env, .git/, and IDE/OS files from build context
├── .gitignore              # Excludes target/, .env, local data folders, and logs/
├── pom.xml                 # Maven project configuration and dependencies
├── src/                    # Java application source code
└── scripts/
    └── check-logs.sh       # Scheduled log scanner for error/warning keywords
```

---

## Environment & Configuration

### Runtime Environment Variables (`app` service)

Configure environment variables in `compose.yml` or via a local, gitignored `.env` file:

| Variable | Example Value | Purpose |
| :--- | :--- | :--- |
| `SPRING_DATA_REDIS_HOST` | `db` | Hostname of the Redis service on `shortlink-net` (must match the Compose service name) |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis listening port |
| `SERVER_PORT` | `8080` | HTTP port the Java application binds to inside the container |

> Note: The `eclipse-temurin` JRE image has no built-in root/database passwords. Any application secrets or external credentials should be supplied via runtime environment variables or secret stores—never hardcoded in `Dockerfile` or committed to Git.

---

## Local Setup & Verification

### Prerequisites

* Docker Engine + Docker Compose v2 (`docker compose version`)
* Git
* Linux / WSL2 shell (`bash`, `cron`) for running `scripts/check-logs.sh` on a schedule

### 1. Validate Configuration & Start Services

```bash
docker compose config
docker compose up -d --build
```

Verify container status and inspect logs:

```bash
docker compose ps
docker compose logs -f
```

### 2. Test Endpoints

Check cache statistics / health endpoint:

```bash
curl -s http://localhost:8080/api/v1/system/cache-stats
```

Example request to shorten a URL (adjust path/JSON keys to match your controller):

```bash
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "[https://example.com/articles/container-networking-guide?utm_source=compose-test](https://example.com/articles/container-networking-guide?utm_source=compose-test)"}'
```

Example request to resolve a short code:

```bash
curl -i http://localhost:8080/<short-code>
```

### 3. Stop or Reset the Stack

```bash
# Stop containers and preserve the redis_data volume
docker compose down

# Stop containers AND delete named volumes (destroys /data persistence)
docker compose down -v
```

---

## Automated Log Monitoring (`cron`)

The log-checking script lives in [`scripts/check-logs.sh`](scripts/check-logs.sh) and runs on the host/WSL side (outside the runtime `Dockerfile`) so it can inspect `docker compose logs` across your services and write alert files to `logs/`.

### 1. Prepare and Test the Script

Ensure `PROJECT_DIR` inside [`scripts/check-logs.sh`](scripts/check-logs.sh) points to your repository path and uses `LF` (`\n`) line endings, then mark it executable and run a manual test:

```bash
chmod +x scripts/check-logs.sh
./scripts/check-logs.sh
```

### 2. Schedule Automatically With `crontab`

Open your crontab (`crontab -e`) and add a `PATH` line plus your schedule entry (for example, every 5 minutes), replacing `/absolute/path/to/shortlink-service` with your repository path:

```cron
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

*/5 * * * * /absolute/path/to/shortlink-service/scripts/check-logs.sh >> /tmp/shortlink-cron.log 2>&1
```

Confirm your crontab entry is saved (`crontab -l`). On WSL2 Ubuntu, check or start the `cron` daemon if it isn't already running:

```bash
sudo service cron start
service cron status
```

---

## Troubleshooting & Common Pitfalls

* **App cannot reach Redis (`UnknownHostException` / connection refused):**
  * Confirm `SPRING_DATA_REDIS_HOST` matches the Compose service name (`db`), not just the container name.
  * Ensure both `app` and `db` are attached to `shortlink-net`.
* **`cron` job doesn't run or fails with command-not-found / wrong directory:**
  * Always set `PATH` at the top of `crontab` and use `cd "$PROJECT_DIR"` inside `scripts/check-logs.sh`.
  * Check `/tmp/shortlink-cron.log` for output or errors.
* **Script fails with `\r: command not found` or syntax errors:**
  * Convert line endings from Windows `CRLF` to Unix `LF` (`dos2unix scripts/check-logs.sh` or configure your editor/Git to use `LF` for `.sh` files).
* **Data disappeared after stopping containers:**
  * Check if you ran `docker compose down -v` (which removes `redis_data`) or omitted the volume mount on `/data`.

---

## AWS Deployment Plan

### Stage 1: Single EC2 Instance With Docker Compose (Start Here)

1. **Launch an EC2 Instance:**
   * Choose Amazon Linux 2023 or Ubuntu LTS in your target AWS Region.
   * Size appropriately for JVM + Redis + OS overhead (e.g., `t3.small` or `t3.medium` rather than undersized micro instances).
   * Attach an IAM role if the instance needs AWS API access (prefer IAM roles over long-lived access keys).
2. **Configure Security Groups:**
   * Allow **SSH (TCP 22)** only from your static/home IP range.
   * Allow **HTTP (TCP 80)** and **HTTPS (TCP 443)** if terminating TLS/reverse-proxying on the host; avoid exposing Redis (`6379`) or internal app ports directly to `0.0.0.0/0`.
3. **Install Docker & Compose:**
   * Install Docker Engine and the Docker Compose CLI plugin, start/enable the Docker service, and add your login user (`ec2-user` or `ubuntu`) to the `docker` group (re-login afterward so group membership applies).
4. **Deploy & Verify on EC2:**
   * Clone the repository, create a local `.env` file if needed, and run:
     ```bash
     docker compose config
     docker compose up -d --build
     docker compose ps
     ```
5. **Enable Scheduled Log Monitoring:**
   * Update `PROJECT_DIR` in `scripts/check-logs.sh`, run `chmod +x scripts/check-logs.sh`, ensure `crond`/`cron` is enabled and running, and add the `crontab` entry.

### Stage 2: Production Hardening on AWS

* **Reverse Proxy & HTTPS:** Put Nginx, Caddy, or an Application Load Balancer (ALB) with AWS Certificate Manager (ACM) TLS in front of the Java service.
* **Centralized Observability & Alerting:** Forward container logs and `scripts/check-logs.sh` alerts to **Amazon CloudWatch Logs** and trigger notifications via **Amazon SNS**.
* **Backups & Disk Hygiene:** Take periodic EBS snapshots or volume backups, and rotate/clean old files in `logs/` so disk usage stays bounded.

### Stage 3: Multi-Instance / High-Availability Evolution

* **Container Orchestration:** Migrate from a single EC2 host to **Amazon ECS** (or EKS) behind an **ALB** for rolling deployments, health-based replacement, and horizontal scaling.
* **Managed Cache & Database:** Move Redis to **Amazon ElastiCache** for multi-AZ resilience/managed maintenance, and add **Amazon RDS (PostgreSQL)** when durable relational persistence is required across instances.

---

## Pre-Deployment Checklist

* [ ] No credentials, API keys, or secrets committed to Git or baked into `Dockerfile` / `compose.yml`.
* [ ] `.gitignore` and `.dockerignore` exclude `.env`, `target/`, and `logs/`.
* [ ] `docker compose config` succeeds with zero warnings or errors.
* [ ] Container runs as non-root (`appuser`) and base images are pinned by SHA-256 digest.
* [ ] `scripts/check-logs.sh` uses `LF` line endings, has `chmod +x`, and has been tested manually and via `cron`.