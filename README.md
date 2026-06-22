# Obsidian Clone

A self-hosted, web-based clone of [Obsidian](https://obsidian.md) — Spring Boot
backend over a filesystem vault of `.md` files, React + CodeMirror 6 frontend
with inline Live Preview. Runs entirely in Docker (WSL-friendly).

> **Status:** M1 in progress — foundation + core editing & linking. See
> [`docs/superpowers/specs/`](docs/superpowers/specs/) for the design and
> [`docs/superpowers/plans/`](docs/superpowers/plans/) for the build plan.

## Architecture

| Layer | Tech |
| --- | --- |
| Backend | Java 21, Spring Boot 3.3 (Gradle), flexmark-java; REST + STOMP WebSocket |
| Frontend | React 18, TypeScript, Vite, CodeMirror 6, Zustand |
| Storage | A **vault folder** of real `.md` files (the source of truth); in-memory link/tag index |
| Packaging | Docker — dev compose (hot reload) + single prod image |

The vault folder is mounted into the backend; an in-memory index (links,
backlinks, tags) is built on boot and kept current by a filesystem watcher.

## Running (WSL Docker)

All commands assume the user's WSL Docker. From the repo root:

### Development (hot reload)

```bash
wsl bash -lc 'cd /mnt/f/Obsidian && docker compose -f docker-compose.dev.yml up --build'
```

- Frontend (the app): <http://localhost:5173>
- Backend API: <http://localhost:8080/api/health>

The Vite dev server proxies `/api` and `/ws` to the backend, so use port **5173**.

### Production (single image)

```bash
wsl bash -lc 'cd /mnt/f/Obsidian && docker compose up --build'
```

- App: <http://localhost:8080>

## The vault

Notes live in [`./vault`](vault/) (mounted into the container at `/vault`).
A few sample notes are included to demonstrate links, embeds, and backlinks.
Your own notes in this folder are git-ignored.

## Testing

```bash
# Backend
wsl bash -lc 'cd /mnt/f/Obsidian && docker compose -f docker-compose.dev.yml run --rm backend gradle --no-daemon test'
# Frontend
wsl bash -lc 'cd /mnt/f/Obsidian && docker compose -f docker-compose.dev.yml run --rm frontend sh -c "npm install && npm test"'
```

> **Note on this environment:** the backend builds with **Gradle** (not Maven)
> and all images use locally-cached bases, because the WSL Docker daemon here
> cannot pull from Docker Hub (DNS). The compose services set `dns: 1.1.1.1` so
> dependency downloads (Gradle/npm) resolve at runtime.
