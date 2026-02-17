# Phase 1.5 — Internal Trial Guide

## Prerequisites

- Docker Engine 24+ and Docker Compose v2
- An Anthropic API Key (`sk-ant-...`)
- At least 4 GB of free memory for Docker

## Quick Start

```bash
cd infrastructure/docker

# 1. Create your env file
cp .env.trial.example .env.trial

# 2. Edit .env.trial and set your real ANTHROPIC_API_KEY
#    ANTHROPIC_API_KEY=sk-ant-api03-xxxx...

# 3. Build and start all services
docker compose -f docker-compose.trial.yml --env-file .env.trial up --build
```

First build takes a few minutes (Gradle build + npm install). Subsequent starts are much faster.

## Access

| Service | URL |
|---------|-----|
| Web IDE | http://localhost:9000 |
| Health Check | http://localhost:9000/actuator/health |
| H2 Console | http://localhost:9000/h2-console/ |

## Verification Steps

1. Open http://localhost:9000 in your browser
2. Create a new Workspace
3. Open the editor
4. Open the AI Chat panel
5. Send a message (e.g., "Hello, explain what you can do")
6. Verify streaming response appears
7. Try a tool call (e.g., "Create a file called hello.txt with content 'Hello World'")
8. Verify the agentic loop completes

## Health Check

```bash
curl http://localhost:9000/actuator/health
```

Expected response: `{"status":"UP"}`

## Stopping

```bash
docker compose -f docker-compose.trial.yml --env-file .env.trial down
```

To also remove built images:

```bash
docker compose -f docker-compose.trial.yml --env-file .env.trial down --rmi local
```

## Known Limitations

- **No persistent storage**: H2 in-memory database; data is lost on restart
- **No authentication**: Security is disabled for trial simplicity
- **No MCP servers**: Knowledge, Database, Service Graph, Artifact, and Observability MCP servers are not included
- **Single-user**: Not designed for concurrent multi-user access
- **Local only**: Binds to localhost; not suitable for remote access without additional configuration

## Troubleshooting

### Backend fails to start
- Check that `ANTHROPIC_API_KEY` is set correctly in `.env.trial`
- Ensure port 9000 is not in use: `lsof -i :9000`

### WebSocket connection fails
- Verify Nginx is running: `docker compose -f docker-compose.trial.yml ps`
- Check Nginx logs: `docker compose -f docker-compose.trial.yml logs nginx`

### Build fails
- Ensure Docker has at least 4 GB of memory allocated
- Try a clean build: `docker compose -f docker-compose.trial.yml --env-file .env.trial build --no-cache`

## Feedback

Please report issues and feedback to the team via:
- GitHub Issues on the forge-platform repository
- Team Slack channel: #forge-trial-feedback
