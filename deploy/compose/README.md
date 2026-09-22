# Docker Compose

## Local isolated stack

`docker-compose.yml` starts dedicated local PostgreSQL, Redis and the platform
application. It is intended for isolated development.

```bash
cd deploy/compose
docker compose up -d
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

Before starting the app, create the PostgreSQL database and user in the infra
PostgreSQL container, then apply migrations from `database/schemas` and
`database/migrations`.
