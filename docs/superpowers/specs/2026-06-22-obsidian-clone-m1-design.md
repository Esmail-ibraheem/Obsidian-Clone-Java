# Obsidian Clone — Design Spec

**Date:** 2026-06-22
**Status:** Approved (brainstorming) → implementation
**Scope of this doc:** Overall architecture + milestone map (context), then the detailed **M1** design that we build first.

---

## 1. Goal

Build a self-hosted, web-based clone of [Obsidian](https://obsidian.md) that works *exactly like Obsidian* on desktop. Backend in **Java 21 + Spring Boot 3.x**; frontend in **React 18 + TypeScript + Vite** with **CodeMirror 6** as the editor (the same engine Obsidian uses). Runs entirely in the user's **WSL Docker** — no native Java/Node/Maven on the Windows host.

### Product decisions (locked during brainstorming)
- **Full target:** everything including Canvas and a plugin system — delivered milestone by milestone.
- **Storage model:** single-user, **vault folder on the filesystem** (real `.md` files on disk). No login. Source of truth = the files.
- **Editing fidelity:** **Obsidian-style inline Live Preview** (not split-pane).
- **First build:** **M1** only (foundation + core editing & linking).
- **Packaging:** dev `docker-compose` with hot reload **plus** a single production image.
- **Targets:** desktop web only (no mobile/responsive yet).

---

## 2. Overall architecture (spans all milestones)

```
┌──────────────────────────── Browser (React + TS + Vite) ────────────────────────────┐
│  Ribbon │ Sidebar (Explorer)  │   Workspace (tabs + split panes, CM6 editors)  │ Backlinks │
│                                                                                       │
│  Zustand stores: vaultStore · workspaceStore · editorStore(per note)                  │
│  REST client (fetch)   +   WebSocket client (STOMP) for live vault events             │
└───────────────────────────────────────┬───────────────────────────────────────────────┘
                                         │ HTTP /api/*  +  WS /ws
┌────────────────────────────────────────▼──────────────────────────────────────────────┐
│                          Spring Boot (Java 21)                                          │
│   api (REST controllers + WebSocket)                                                     │
│   index (flexmark parse → LinkGraph: outlinks/backlinks, tags, headings; full-text)      │
│   watch (java.nio WatchService → FileChangeEvent via ApplicationEventPublisher)          │
│   vault (VaultService CRUD, VaultPathResolver traversal-safety, VaultConfig root)         │
└────────────────────────────────────────┬──────────────────────────────────────────────┘
                                          │ reads/writes
                              ┌───────────▼───────────┐
                              │  Vault folder (volume) │  ← real .md files + attachments
                              └────────────────────────┘
```

- **Persistence:** the vault folder *is* the database. The link/tag/search index is **in memory**, rebuilt on boot and updated incrementally on file changes. (Postgres/SQLite/Lucene considered and deferred until vault scale demands it.)
- **Live sync:** backend `WatchService` detects on-disk changes (including external edits) → reindex → push events over WebSocket so the explorer and open editors stay current.
- **Plugin readiness:** Obsidian plugins live in the frontend, so we design clean frontend API boundaries (`App` / `Vault` / `Workspace`-like surfaces) from day one; the backend stays plugin-agnostic.

### Key technology choices (with rejected alternatives)
| Axis | Choice | Rejected | Why |
| --- | --- | --- | --- |
| Editor | **CodeMirror 6** | Monaco, ProseMirror/contentEditable | CM6 is what Obsidian uses; only realistic path to faithful inline Live Preview over a markdown-source model. |
| Frontend framework | **React + TS + Vite** | Svelte, Vue | Most mature CM6 integration & reference material. (User delegated this choice.) |
| Frontend state | **Zustand** | Redux | Lightweight, low boilerplate, fine for this app. |
| Markdown (backend) | **flexmark-java** | commonmark-java | Rich extension model incl. wikilinks; AST access for link/tag/heading extraction. |
| Live preview | **CM6 decoration view plugin** | dual-pane preview | Faithful Obsidian feel (user-selected). |
| Search (later) | in-memory inverted index → **Lucene** upgrade | — | Start simple; Lucene only when scale needs it. |

---

## 3. Milestone map

| # | Milestone | Contents |
| --- | --- | --- |
| **M1** | **Foundation + core editing & linking** | App shell/layout · vault service · file-tree explorer (CRUD/move) · CM6 inline Live Preview · `[[wikilinks]]` (autocomplete/navigate/create/`\|alias`) · `![[embeds]]` · backlinks panel · tabs + split panes · autosave · live file-watch sync · link-update-on-rename · dark theme · Dockerized (dev + prod). |
| M2 | Navigation & search | Quick switcher (Ctrl+O) · command palette (Ctrl+P) · global full-text search · tags + tag pane · outline/TOC · bookmarks. |
| M3 | Graph view | Global + local force-directed graph, filters, navigation. |
| M4 | Daily notes & theming | Daily notes + calendar · templates · light/dark theme system · settings UI scaffold. |
| M5 | Canvas | Infinite whiteboard · card/note/media nodes · edges · `.canvas` JSON (Obsidian format). |
| M6 | Plugin system | Sandboxed frontend plugin API · plugin manager UI · sample plugins. |

Each milestone after M1 gets its own spec → plan → build cycle.

---

## 4. M1 detailed design

### 4.1 Scope
**In:** app shell/layout; vault filesystem service; file-tree explorer with create/rename/delete/move + context menu; CM6 editor with **inline Live Preview**; `[[wikilinks]]` (autocomplete, navigate, create-on-click, `[[note|alias]]`); `![[embeds]]` (note transclusion + images); backlinks panel; tabs + split panes; debounced autosave; live file-watch sync over WebSocket; link-update-on-rename; dark theme; Docker dev + prod.

**Deferred:** search/quick-switcher/command-palette (M2), tags pane & outline UI (M2 — tags & headings *are* indexed in M1, just no dedicated UI), graph (M3), light theme/settings (M4), canvas (M5), plugins (M6), drag-to-reorder tabs (nice-to-have), full block-reference (`^id`) resolution (basic parse only in M1).

### 4.2 Backend modules
- **`vault`**
  - `VaultConfig` — resolves vault root from config/env (`vault.root`), defaults to `/vault` in container.
  - `VaultPathResolver` — turns a client-supplied relative path into an absolute path **inside** the vault; rejects traversal (`..`, absolute paths, symlink escape) → `IllegalArgumentException` → HTTP 400.
  - `VaultService` — `tree()`, `read(path)`, `write(path, content, baseMtime)`, `createFile/createFolder`, `rename(from,to)`, `delete(path)`. Returns metadata incl. `mtime`, `size`. Binary attachments streamed.
- **`watch`**
  - `VaultWatcher` — registers vault dir tree with `java.nio.file.WatchService`; debounces and publishes `FileChangeEvent{type, path}` via `ApplicationEventPublisher`. Recursive registration of new subfolders.
- **`index`**
  - `MarkdownParser` — flexmark; extracts `LinkRef{target, alias, anchorType(heading/block), anchor}`, embeds, `#tags`, headings.
  - `LinkGraph` — `Map<NotePath, Set<ResolvedLink>>` + reverse index for backlinks. **Obsidian-style resolution:** match `[[Name]]` by basename; if ambiguous, shortest path; fall back to literal path. Tracks unresolved links (for create-on-click + future "create" UX).
  - `IndexService` — builds full index on boot; `onFileChange` updates incrementally; exposes `backlinks(path)`, `resolve(linkText, fromPath)`, `tags()`, `headings(path)`.
- **`api`**
  - `FileController` — `GET /api/vault/tree`; `GET /api/files?path=`; `PUT /api/files?path=` (body=content, header/param `baseMtime`); `POST /api/files` (`{path, type:file|folder, content?}`); `DELETE /api/files?path=`; `POST /api/files/rename` (`{from, to, updateLinks:true}`).
  - `BacklinkController` — `GET /api/backlinks?path=` → `[{sourcePath, snippet, line}]`.
  - `AttachmentController` (or `FileController` content-negotiated) — streams images/binaries with correct content type.
  - `WebSocketConfig` (STOMP, endpoint `/ws`, broker `/topic`) + `VaultEventBroadcaster` — `@EventListener` on `FileChangeEvent` → send to `/topic/vault` `{type, path, mtime}`.
- **Conflict handling:** `PUT` compares `baseMtime` to current on-disk mtime; mismatch → **409** with current server content so the client can prompt/merge.

### 4.3 Frontend structure
- **Layout:** `App` → `Workspace` with three regions — left **Ribbon** (icon bar) + resizable **Sidebar** (FileExplorer), center **WorkspaceArea** (tabs + recursive split panes), right **Sidebar** (BacklinksPanel). Resizable splitters; collapsible sidebars.
- **State (Zustand):**
  - `vaultStore` — file tree; updated by REST load + WS events.
  - `workspaceStore` — pane tree (leaf = tab group), open tabs, active tab/pane, split/close operations.
  - `editorStore` — per open note: `content`, `dirty`, `baseMtime`, `loading`.
- **Editor (CM6) — the centerpiece:**
  - Base: `@codemirror/lang-markdown`, dark theme, keymaps.
  - **Live Preview view plugin** — a `ViewPlugin` producing a `DecorationSet` each update: hide syntax tokens & style rendered content for lines **not** containing the cursor/selection; show raw source on the active line(s); replace `[[wikilinks]]`/`![[embeds]]`/images with clickable widgets; render headings, bold/italic, lists, blockquotes, inline code, fenced code, HR, links. Reuses the parsed Lezer markdown tree.
  - **Wikilink autocomplete:** `@codemirror/autocomplete` source triggered after `[[`, completing against the vault file list (and headings for `#`, blocks for `^`).
  - **Autosave:** debounce (≈400 ms) on doc change → `PUT` → update `baseMtime`, clear `dirty`.
  - **External-change reconciliation:** on WS `modified` for an open file — reload doc if not `dirty`; if `dirty`, show conflict prompt (keep mine / load theirs).
- **Comms:** `apiClient` (fetch wrappers) + `wsClient` (STOMP/SockJS) with auto-reconnect; on reconnect, refetch tree + active file to resync.

### 4.4 Data flow
1. **Open:** explorer click → `workspaceStore.openTab(path)` → `EditorPane` fetches `GET /api/files` → loads CM6 doc + `baseMtime`.
2. **Edit:** CM6 change → `editorStore` marks dirty → debounce → `PUT` → backend writes + `IndexService.onFileChange` → WS `modified` echo (origin reconciles by mtime, no reload loop).
3. **External edit:** watcher → reindex → WS `modified` → clean editors reload, dirty editors prompt.
4. **Backlinks:** active note change → `GET /api/backlinks` → render; refresh when WS events touch the link graph for that note.
5. **Rename:** `POST /api/files/rename` with `updateLinks` → backend moves file + rewrites `[[links]]` in backlinking notes → WS `renamed` + `modified` events → clients update tabs/explorer/editors.

### 4.5 Error handling
- Path traversal → **400**; missing file → **404**; mtime conflict → **409** (+ server content); create-existing → **409**; vault root missing → backend creates it, frontend shows empty state.
- WS disconnect → exponential-backoff reconnect → full resync on reconnect.
- Backend validates all paths through `VaultPathResolver` before any filesystem touch.

### 4.6 Testing (TDD throughout — superpowers TDD)
- **Backend (JUnit 5 + Spring Boot Test):**
  - `VaultPathResolver` — traversal/symlink-escape rejection (security-critical).
  - `MarkdownParser` — link/alias/embed/tag/heading extraction edge cases.
  - `LinkGraph` — Obsidian-style resolution, ambiguity, unresolved links, backlink correctness, incremental update.
  - `VaultService` — CRUD/rename/delete on `@TempDir`.
  - REST — MockMvc for each endpoint incl. 400/404/409 paths.
  - WebSocket — event broadcast on file change (integration).
- **Frontend (Vitest + React Testing Library):**
  - Stores (vault/workspace/editor) reducers/actions.
  - **Live Preview decoration logic** (parse → expected `DecorationSet`) — the riskiest unit, tested directly.
  - Wikilink autocomplete source; conflict reconciliation logic.
  - Optional Playwright e2e: open → type → see live preview → click `[[link]]` → navigate → see backlink.

### 4.7 Packaging & running (WSL Docker)
- **`docker-compose.dev.yml`:**
  - `backend` — image from `backend/Dockerfile.dev` (Eclipse Temurin JDK 21 + Maven), runs `mvn spring-boot:run` with **DevTools**; mounts `./backend` (source) + the **vault volume**; `:8080`.
  - `frontend` — node image, `npm run dev` (Vite HMR); mounts `./frontend`; `:5173`; Vite proxy forwards `/api` and `/ws` → backend.
  - vault volume: host `./vault` (a `.gitkeep`'d, git-ignored folder seeded with a few sample notes) mounted to `/vault` in backend.
- **Production:** multi-stage `Dockerfile` at repo root — (1) node build → static assets, (2) Maven build → jar that serves the assets from `classpath:/static`, (3) slim Temurin JRE runtime. `docker-compose.yml` runs the single image with the vault mounted, `:8080`.
- All commands invoked through the user's WSL Docker (`docker compose ...`).

### 4.8 Repository layout
```
f:/Obsidian/
  backend/                 Spring Boot (Maven) — Java 21
    src/main/java/...       vault · watch · index · api
    src/test/java/...
    Dockerfile  Dockerfile.dev  pom.xml
  frontend/                React + TS + Vite
    src/  (components, stores, editor/livepreview, api, ws)
    Dockerfile  package.json  vite.config.ts
  docs/superpowers/specs/  this spec + future milestone specs
  vault/                   dev sample vault (git-ignored, .gitkeep)
  docker-compose.yml       prod
  docker-compose.dev.yml   dev (hot reload)
  README.md
```

### 4.9 Risk callouts
- **Inline Live Preview is the hard ~30% of M1** (CM6 decoration bookkeeping for cursor-aware reveal). Fallback if it threatens delivery: render-on-blur or a temporary split-preview toggle — but the target is the real inline experience.
- **Link-update-on-rename** included for fidelity; feasible via the link graph; covered thoroughly in tests.

---

## 5. Definition of done for M1
- `docker compose -f docker-compose.dev.yml up` brings up a working app in WSL Docker; opening `localhost:5173` shows the vault.
- Can create/open/edit/rename/delete notes & folders from the explorer; edits autosave to disk and survive restart.
- Inline Live Preview renders markdown with cursor-aware source reveal.
- `[[wikilinks]]` autocomplete, navigate, create-on-click, alias all work; `![[embeds]]` render; backlinks panel is correct.
- Tabs + split panes work; external/CLI edits to vault files reflect live in the UI.
- Renaming a note rewrites its backlinks.
- `docker compose up` (prod image) serves the same app from a single container.
- Backend + frontend test suites pass.
