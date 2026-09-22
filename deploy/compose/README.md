# Docker Compose

## Local isolated stack

`docker-compose.yml` starts dedicated PostgreSQL, Redis and the PostgreSQL-backed
platform API. It is intended for isolated development. Database schema and
migrations are mounted into a new PostgreSQL volume during initialization.

```bash
cd deploy/compose
docker compose up -d
```

To run the real model Worker, build both application images as documented in
`deploy/README.md`, then start its opt-in profile. The local
CLIProxyAPI container must already be attached to `infra-stack_infra`.

```bash
CLIPROXY_API_KEY=... docker compose --profile worker up -d agent-worker
```

## Existing infra stack

`docker-compose.infra.yml` attaches the application to the existing
`infra-stack_infra` Docker network and reuses:

- `infra-postgres` on the Docker network as PostgreSQL
- `infra-redis` on the Docker network as Redis

Use a project-specific database/user and Redis DB. Keep the real `.env.infra`
out of git.

```bash
cd deploy/compose
cp .env.infra.example .env.infra
docker compose --env-file .env.infra -f docker-compose.infra.yml up -d
```

Set `EAP_IMAGE`, `EAP_IMAGE_TAG`, `EAP_WORKER_IMAGE`, `EAP_WORKER_IMAGE_TAG`,
`EAP_MODEL_AGENT_IDS` and the `EAP_CLIPROXY_*` variables in `.env.infra`.
The Worker and CLIProxyAPI must share the `infra` network, where the proxy
resolves as `infra-cli-proxy-api`.

Before starting the app, create the PostgreSQL database and user in the infra
PostgreSQL container, then apply migrations from `database/schemas` and
`database/migrations`.
