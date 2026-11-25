# Shortlink Service

A containerized Java 21 service backed by Redis, orchestrated with Docker Compose, monitored via scheduled log checks, and structured for phased AWS deployment.

## Architecture

* **Application (`app`):** Java 21 runtime built via a multi-stage Dockerfile and executed as a non-root user on port `8080`.
* **Cache / Store (`db`):** Redis 8 Alpine configured with `256mb` memory limit (`allkeys-lru`), AOF persistence (`--appendonly yes`), and a named volume (`redis_data:/data`).
* **Networking:** Services communicate over a dedicated bridge network (`shortlink-net`).
* **Observability:** A host-side `cron` job runs [`scripts/check-logs.sh`](scripts/check-logs.sh) at regular intervals to scan recent container logs for error keywords.

## Environment Variables

| Variable | Default / Example | Description |
| :--- | :--- | :--- |
| `SPRING_DATA_REDIS_HOST` | `db` | Redis service hostname on `shortlink-net` |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis service port |
| `SERVER_PORT` | `8080` | Application HTTP port |

## Local Development

Validate configuration and start the stack:

```bash
docker compose config
docker compose up -d --build
```

Inspect service health and logs:

```bash
docker compose ps
docker compose logs -f
```

Stop containers while preserving the `redis_data` volume:

```bash
docker compose down
```

## Scheduled Log Monitoring

Configure `PROJECT_DIR` in [`scripts/check-logs.sh`](scripts/check-logs.sh), ensure `LF` line endings, and enable execution:

```bash
chmod +x scripts/check-logs.sh
```

Schedule via `crontab -e`:

```cron
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

*/5 * * * * /path/to/repo/scripts/check-logs.sh >> /tmp/shortlink-cron.log 2>&1
```

## AWS Deployment Roadmap

1. **Single-Host Baseline (EC2):** Provision an EC2 instance (`t3.small` or higher), restrict security group ingress (SSH to trusted IPs; HTTP/HTTPS as needed), install Docker and Compose, deploy with `docker compose up -d --build`, and schedule [`scripts/check-logs.sh`](scripts/check_logs.sh).
2. **Hardening & Observability:** Place a reverse proxy or Application Load Balancer with TLS in front of the service, forward logs to CloudWatch, and configure SNS alerts and log/volume retention.
3. **Horizontal Scale & Managed State:** Transition compute to Amazon ECS behind an ALB, offload caching to Amazon ElastiCache, and introduce Amazon RDS if relational persistence becomes required.