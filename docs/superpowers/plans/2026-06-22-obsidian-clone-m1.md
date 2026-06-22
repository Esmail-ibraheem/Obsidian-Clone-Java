# Obsidian Clone — M1 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build M1 of an Obsidian clone — a self-hosted web app (Spring Boot + React/CodeMirror 6) that opens a filesystem vault of `.md` files and gives the recognizable Obsidian core: file explorer, inline Live Preview editor, `[[wikilinks]]` + `![[embeds]]`, backlinks, tabs/split panes, live file-watch sync — all running in WSL Docker.

**Architecture:** Spring Boot owns the vault folder (the source of truth) and an in-memory link/tag index rebuilt from files and kept current by a `WatchService`; it exposes REST + a STOMP WebSocket. A React + TypeScript + Vite frontend uses CodeMirror 6 with a cursor-aware decoration plugin for inline Live Preview, talks to the backend over fetch + STOMP, and holds workspace state in Zustand.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Maven, flexmark-java, JUnit 5; React 18, TypeScript, Vite, CodeMirror 6, Zustand, @stomp/stompjs + SockJS, Vitest + React Testing Library; Docker + docker-compose (dev hot-reload + prod single image).

**Spec:** `docs/superpowers/specs/2026-06-22-obsidian-clone-m1-design.md`

**Conventions (apply to every task):**
- Backend base package: `com.obsidianclone`. Modules are packages: `vault`, `watch`, `index`, `api`, `config`.
- All client-supplied paths are vault-relative, POSIX-style (`/`-separated), never absolute. Every filesystem touch goes through `VaultPathResolver` first.
- Frontend path alias `@/` → `frontend/src/`. Components PascalCase, stores camelCase `useXStore`.
- TDD: write the failing test, watch it fail, implement minimal code, watch it pass, commit. One logical change per commit; messages `feat:`/`test:`/`chore:`/`fix:`/`refactor:`.
- Reference skills: @superpowers:test-driven-development for every task, @superpowers:systematic-debugging when a test won't pass.
- Run backend tests in Docker: `docker compose -f docker-compose.dev.yml run --rm backend mvn -q test` (or `... -Dtest=ClassName test`). Run frontend tests: `docker compose -f docker-compose.dev.yml run --rm frontend npm test`. (Native `mvn`/`npm` are NOT installed on the host — always go through Docker/WSL.)

---

## File structure (created across M1)

```
backend/
  pom.xml
  Dockerfile           # prod stage target
  Dockerfile.dev       # JDK 21 + Maven, spring-boot:run + DevTools
  src/main/resources/application.yml
  src/main/java/com/obsidianclone/
    ObsidianCloneApplication.java
    config/ VaultProperties.java  WebConfig.java
    vault/  VaultPathResolver.java  VaultService.java
            FileNode.java  NoteContent.java  VaultException.java
    watch/  VaultWatcher.java  FileChangeEvent.java  ChangeType.java
    index/  MarkdownParser.java  ParsedNote.java  LinkRef.java
            LinkGraph.java  IndexService.java  BacklinkEntry.java
    api/    FileController.java  BacklinkController.java
            AttachmentController.java  HealthController.java
            WebSocketConfig.java  VaultEventBroadcaster.java  ApiExceptionHandler.java
            dto/ (request/response records)
  src/test/java/com/obsidianclone/...   # mirrors main
frontend/
  package.json  tsconfig.json  vite.config.ts  index.html  Dockerfile
  src/
    main.tsx  App.tsx  styles/theme.css
    api/ client.ts  types.ts
    ws/  wsClient.ts
    stores/ vaultStore.ts  workspaceStore.ts  editorStore.ts
    components/
      layout/ Ribbon.tsx  Sidebar.tsx  Workspace.tsx  Splitter.tsx
      explorer/ FileExplorer.tsx  TreeNode.tsx  ExplorerContextMenu.tsx
      workspace/ PaneTree.tsx  TabBar.tsx  EditorPane.tsx
      backlinks/ BacklinksPanel.tsx
    editor/
      createEditor.ts  markdownConfig.ts
      livePreview/ livePreviewPlugin.ts  decorations.ts  widgets.tsx
      wikilink/ wikilinkAutocomplete.ts  wikilinkNavigate.ts
      autosave.ts  externalSync.ts
    test/ setup.ts
docker-compose.dev.yml
docker-compose.yml
README.md
vault/                 # dev sample vault (git-ignored), seeded with sample notes
```

---

## Phase 0 — Walking skeleton (dev + prod Docker run end-to-end)

Goal: `docker compose -f docker-compose.dev.yml up` serves a React page at `:5173` that successfully calls `GET /api/health` on the backend at `:8080` — before any feature code. This de-risks the toolchain first.

### Task 0.1: Backend Maven skeleton
**Files:** Create `backend/pom.xml`, `backend/src/main/java/com/obsidianclone/ObsidianCloneApplication.java`, `backend/src/main/resources/application.yml`, `backend/src/main/java/com/obsidianclone/api/HealthController.java`.

- [ ] **Step 1:** Create `pom.xml` — Spring Boot 3.3.x parent, Java 21, deps: `spring-boot-starter-web`, `spring-boot-starter-websocket`, `com.vladsch.flexmark:flexmark-all:0.64.8`, `spring-boot-devtools` (optional/runtime), `spring-boot-starter-test` (test). Plugin: `spring-boot-maven-plugin`.
- [ ] **Step 2:** `ObsidianCloneApplication` with `@SpringBootApplication` + `main`.
- [ ] **Step 3:** `HealthController` — `@GetMapping("/api/health")` returns `{"status":"ok"}`.
- [ ] **Step 4:** `application.yml` — `server.port: 8080`, `vault.root: ${VAULT_ROOT:/vault}`, logging.
- [ ] **Step 5 (test):** `HealthControllerTest` with `@WebMvcTest(HealthController.class)` asserting `GET /api/health` → 200 and `status=ok`. Run: `docker compose -f docker-compose.dev.yml run --rm backend mvn -q -Dtest=HealthControllerTest test` → PASS (after Task 0.3 builds the image; until then defer the run).
- [ ] **Step 6:** Commit `feat(backend): spring boot skeleton + health endpoint`.

### Task 0.2: Frontend Vite skeleton
**Files:** Create `frontend/package.json`, `tsconfig.json`, `vite.config.ts`, `index.html`, `src/main.tsx`, `src/App.tsx`, `src/api/client.ts`.

- [ ] **Step 1:** `package.json` — React 18, TS, Vite, `vitest`, `@testing-library/react`, `jsdom`, CodeMirror 6 packages (`@codemirror/state`, `@codemirror/view`, `@codemirror/commands`, `@codemirror/language`, `@codemirror/lang-markdown`, `@codemirror/autocomplete`, `@lezer/markdown`), `zustand`, `@stomp/stompjs`, `sockjs-client`. Scripts: `dev`, `build`, `preview`, `test`.
- [ ] **Step 2:** `vite.config.ts` — React plugin, `@` alias, `server.host: true`, `server.port: 5173`, proxy `/api` and `/ws` → `http://backend:8080` (service name in compose), `ws: true` for `/ws`.
- [ ] **Step 3:** `App.tsx` — fetch `/api/health` on mount, render the status string. (Temporary; replaced in Phase 5.)
- [ ] **Step 4 (test):** `App.test.tsx` (Vitest + RTL) — mock fetch, assert it renders "ok". Run after image exists.
- [ ] **Step 5:** Commit `feat(frontend): vite react skeleton calling /api/health`.

### Task 0.3: Docker dev + prod + sample vault
**Files:** Create `backend/Dockerfile.dev`, `backend/Dockerfile`, `frontend/Dockerfile`, `docker-compose.dev.yml`, `docker-compose.yml`, `vault/.gitkeep`, a few `vault/*.md` sample notes, `README.md`.

- [ ] **Step 1:** `backend/Dockerfile.dev` — `eclipse-temurin:21-jdk` + Maven; workdir `/app`; `CMD mvn spring-boot:run`. (Source bind-mounted by compose; DevTools restarts on change.)
- [ ] **Step 2:** `backend/Dockerfile` (prod) — placeholder note; the real multi-stage prod image is finalized in Phase 8. For now a single Maven build → `java -jar`.
- [ ] **Step 3:** `frontend/Dockerfile` — `node:20`; `CMD ["npm","run","dev","--","--host"]`.
- [ ] **Step 4:** `docker-compose.dev.yml` — services `backend` (build Dockerfile.dev, mount `./backend:/app` + named volume for `~/.m2`, mount `./vault:/vault`, env `VAULT_ROOT=/vault`, port `8080:8080`) and `frontend` (build frontend Dockerfile, mount `./frontend:/app` + anon volume for `node_modules`, port `5173:5173`, `depends_on: backend`). Shared network.
- [ ] **Step 5:** Seed `vault/` with sample notes that exercise links: `Welcome.md` (links `[[Getting Started]]`, embeds `![[diagram.png]]` placeholder, a `#tag`), `Getting Started.md`, `Folder/Nested Note.md`.
- [ ] **Step 6:** `README.md` — how to run: `docker compose -f docker-compose.dev.yml up`, open `http://localhost:5173`; prod: `docker compose up`.
- [ ] **Step 7 (verify):** `docker compose -f docker-compose.dev.yml up --build` in WSL; confirm `:5173` renders "ok" (frontend → proxy → backend health). Run the deferred tests from 0.1/0.2.
- [ ] **Step 8:** Commit `chore: dockerized dev + prod skeleton with sample vault`.

---

## Phase 1 — Backend `vault` module (filesystem CRUD + path safety)

### Task 1.1: `VaultProperties` + `VaultPathResolver` (security-critical)
**Files:** Create `config/VaultProperties.java`, `vault/VaultPathResolver.java`, `vault/VaultException.java`; Test `vault/VaultPathResolverTest.java`.

- [ ] **Step 1 (failing tests):** `VaultPathResolverTest` covering:
  - `resolve("Notes/a.md")` returns an absolute path under the vault root.
  - `resolve("../escape.md")` throws `VaultException` (traversal).
  - `resolve("/etc/passwd")` (absolute) throws.
  - `resolve("a/../../b")` (normalizes outside) throws.
  - `resolve("")` and `resolve("/")` resolve to the vault root.
  - A path whose normalized real path escapes the root via symlink throws (use `@TempDir`, create a symlink if supported; skip on platforms without symlink permission).
- [ ] **Step 2:** Run → FAIL (class missing).
- [ ] **Step 3:** Implement `VaultProperties` (`@ConfigurationProperties("vault")`, `root: Path`). Implement `VaultPathResolver`: normalize `root.resolve(relative).normalize()`, require `startsWith(root)`; reject absolute inputs; use `toRealPath`/`toAbsolutePath().normalize()` to defeat symlink escape. Throw `VaultException` otherwise.
- [ ] **Step 4:** Run → PASS.
- [ ] **Step 5:** Commit `feat(vault): path resolver with traversal/symlink safety`.

### Task 1.2: `FileNode` + `VaultService.tree()`
**Files:** Create `vault/FileNode.java` (record: `name`, `path`, `type` file|folder, `children`), `vault/VaultService.java`; Test `vault/VaultServiceTreeTest.java`.

- [ ] **Step 1 (test):** On a `@TempDir` vault with nested files/folders, `tree()` returns a sorted tree (folders first, then files, alpha), excludes hidden/dot files, uses vault-relative POSIX paths.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement `tree()` with `Files.walk`/recursive listing → `FileNode`. Inject `VaultProperties` + `VaultPathResolver`.
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat(vault): file tree listing`.

### Task 1.3: `VaultService` read/write/create/delete/rename
**Files:** Modify `vault/VaultService.java`; Create `vault/NoteContent.java` (record: `path`, `content`, `mtime`, `size`); Test `vault/VaultServiceCrudTest.java`.

- [ ] **Step 1 (tests):** read returns content + mtime; write creates/overwrites and returns new mtime; write with stale `baseMtime` throws `VaultConflictException` (carrying current content); createFile rejects existing → conflict; createFolder; delete file & delete folder (recursive); rename moves file and preserves content; all reject traversal paths.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement using `Files.*`; mtime via `Files.getLastModifiedTime().toMillis()`; conflict = compare `baseMtime` to current. Create `VaultConflictException extends VaultException` holding current `NoteContent`.
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat(vault): note CRUD + rename with mtime conflict detection`.

---

## Phase 2 — Backend `index` module (markdown parse + link graph)

### Task 2.1: `MarkdownParser` (extract links, embeds, tags, headings)
**Files:** Create `index/LinkRef.java` (record: `rawTarget`, `displayAlias`, `anchorType` NONE|HEADING|BLOCK, `anchor`, `isEmbed`, `lineFrom`, `lineTo`), `index/ParsedNote.java` (record: `path`, `links` List<LinkRef>, `tags` Set<String>, `headings` List<String>), `index/MarkdownParser.java`; Test `index/MarkdownParserTest.java`.

- [ ] **Step 1 (tests):** parse a markdown string and assert extraction of:
  - `[[Note]]`, `[[Note|Alias]]`, `[[Note#Heading]]`, `[[Note^block]]`, `![[Note]]` (embed), `![[image.png]]` (embed image).
  - inline `#tag`, `#nested/tag`; **not** inside code fences/inline code; **not** a markdown heading `# Title`.
  - headings list (levels + text) for link targets/outline.
  - multiple links on one line; positions (line numbers) captured.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement with flexmark: enable a wiki-link extension or post-process the AST/text. Use regex on text nodes for `(!?)\[\[([^\]]+)\]\]` splitting `target|alias` and `target#heading` / `target^block`; tags via `(?<!\w)#[\w/-]+` excluding code spans (walk AST, skip `Code`/`FencedCodeBlock`).
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat(index): markdown parser for links, embeds, tags, headings`.

### Task 2.2: `LinkGraph` (Obsidian-style resolution + backlinks)
**Files:** Create `index/LinkGraph.java`, `index/BacklinkEntry.java` (record: `sourcePath`, `line`, `snippet`); Test `index/LinkGraphTest.java`.

- [ ] **Step 1 (tests):**
  - `resolve("Note", from)` matches `Note.md` by basename anywhere in the vault.
  - ambiguous basename → resolves to shortest path / same-folder preference; deterministic.
  - link to non-existent target → recorded as unresolved (resolve returns null/empty).
  - `backlinks("Target.md")` returns every note containing a link that resolves to it, with line + snippet.
  - alias and `#heading`/`^block` anchors don't change resolution target (still resolve to the note).
  - `![[embed]]` counts as a link for backlinks.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement: maintain `Map<path, ParsedNote>`, a basename→paths multimap, forward links (resolved), and reverse (backlinks) index. Resolution order: exact relative path → basename unique → shortest path among matches.
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat(index): link graph with Obsidian-style resolution + backlinks`.

### Task 2.3: `IndexService` (build on boot + incremental update)
**Files:** Create `index/IndexService.java`; Test `index/IndexServiceTest.java`.

- [ ] **Step 1 (tests):** building over a `@TempDir` vault populates the graph; `onFileChanged(path)` reparses just that file and updates forward/backlinks; `onFileDeleted(path)` removes it and its backlinks; `onFileRenamed(from,to)` moves entries. `backlinks(path)` delegates to graph.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement; `@PostConstruct` full build by walking the vault (markdown only) via `VaultService`. Thread-safe (synchronize or `ReadWriteLock`) since the watcher updates concurrently with API reads.
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat(index): index service with incremental updates`.

---

## Phase 3 — Backend `watch` module (live file events)

### Task 3.1: `VaultWatcher` + `FileChangeEvent`
**Files:** Create `watch/ChangeType.java` (enum CREATED|MODIFIED|DELETED), `watch/FileChangeEvent.java` (extends `ApplicationEvent`, fields type+path), `watch/VaultWatcher.java`; Test `watch/VaultWatcherIT.java`.

- [ ] **Step 1 (test, integration):** start watcher on a `@TempDir`; create/modify/delete a file; assert a matching `FileChangeEvent` is published within a timeout (use an `@EventListener` test bean + `Awaitility` or a `CountDownLatch`). Recursive: creating a subfolder + file in it also fires.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement with `WatchService` on a **dedicated thread** started `@PostConstruct`, stopped `@PreDestroy` (prefer this over `@Async`, which would also need `@EnableAsync`). Register root + subdirs recursively; register newly-created dirs. Debounce duplicate events (coalesce by path within ~150 ms). Publish via `ApplicationEventPublisher`. Map relative path.
- [ ] **Step 3a (WSL bind-mount gotcha — REQUIRED):** Native inotify `WatchService` events are **unreliable across the Windows↔WSL bind-mount** (`./vault` on the host). The live-sync DoD depends on this working in the running dev container, not just in `@TempDir` tests. Implement a `WatchStrategy` abstraction with two impls — `NativeWatchStrategy` (WatchService) and `PollingWatchStrategy` (a scheduled directory scan diffing mtimes/size every ~1 s) — selected by config `vault.watch.mode: native|polling|auto` (default `auto`: try native, fall back to polling if no events fire on a self-test write). Default the dev compose to `polling` to avoid burning time debugging "watcher passes tests but does nothing in the container." Add a test for `PollingWatchStrategy` detecting create/modify/delete on a `@TempDir`.
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat(watch): recursive vault watcher publishing change events`.

### Task 3.2: Wire watcher → index
**Files:** Modify `index/IndexService.java` (add `@EventListener onFileChange`).

- [ ] **Step 1 (test):** integration — change a file on disk in a `@TempDir` vault, assert the index/backlinks update (watcher → event → index). 
- [ ] **Step 2-4:** Implement listener routing CREATED/MODIFIED→`onFileChanged`, DELETED→`onFileDeleted`. PASS.
- [ ] **Step 5:** Commit `feat(index): update index from filesystem watch events`.

---

## Phase 4 — Backend `api` (REST + WebSocket + errors)

### Task 4.1: `ApiExceptionHandler`
**Files:** Create `api/ApiExceptionHandler.java`; Test covered via controller tests.
- [ ] `@RestControllerAdvice` mapping `VaultException`→400, `NoSuchFileException`/not-found→404, `VaultConflictException`→409 with body `{message, currentContent, currentMtime}`. Commit `feat(api): exception handler with 400/404/409 mapping`.

### Task 4.2: `FileController` — tree + read + write
**Files:** Create `api/FileController.java`, `api/dto/*`; Test `api/FileControllerTest.java` (`@WebMvcTest` + mocked `VaultService`).

- [ ] **Step 1 (tests):** `GET /api/vault/tree`→tree JSON; `GET /api/files?path=`→content+mtime; `GET` missing→404; `PUT /api/files?path=` body=content, `baseMtime` param → saves, returns new mtime; stale `baseMtime`→409 with current content; traversal path→400.
- [ ] **Step 2:** FAIL. **Step 3:** Implement controller delegating to `VaultService`. **Step 4:** PASS. **Step 5:** Commit `feat(api): file tree/read/write endpoints`.

### Task 4.3: `FileController` — create / delete / rename(+link update)
**Files:** Modify `api/FileController.java`; Modify `index/IndexService.java` + `vault/VaultService.java` for link rewrite; Test add to `FileControllerTest` + new `RenameLinkUpdateTest`.

- [ ] **Step 1 (tests):** `POST /api/files {path,type,content?}` creates; existing→409. `DELETE /api/files?path=` removes. `POST /api/files/rename {from,to,updateLinks:true}`: file moves AND every backlinking note has its `[[from]]`/`[[from|alias]]`/`![[from]]` rewritten to `[[to]]` preserving alias/anchor; returns list of modified files.
- [ ] **Step 2:** FAIL.
- [ ] **Step 3:** Implement. Link rewrite: ask `IndexService.backlinks(from)`; for each source, replace occurrences of the resolved target name with the new name (preserve `|alias`, `#heading`, `^block`, embed `!`). Write each via `VaultService`. Reindex affected.
- [ ] **Step 4:** PASS. **Step 5:** Commit `feat(api): create/delete/rename with backlink rewrite`.

### Task 4.4: `BacklinkController` + `AttachmentController`
**Files:** Create `api/BacklinkController.java`, `api/AttachmentController.java`; Tests.
- [ ] `GET /api/backlinks?path=`→`[{sourcePath,line,snippet}]`. `GET /api/attachments/**` (or `/api/files` with binary content type) streams images with correct `Content-Type` + caching headers; traversal→400. Commit `feat(api): backlinks + attachment streaming`.

### Task 4.5: WebSocket — `WebSocketConfig` + `VaultEventBroadcaster`
**Files:** Create `api/WebSocketConfig.java`, `api/VaultEventBroadcaster.java`; Test `api/WebSocketIT.java`.

- [ ] **Step 1 (test, integration):** connect a STOMP client to `/ws`, subscribe `/topic/vault`, trigger a `FileChangeEvent`, assert a message `{type,path,mtime}` is received.
- [ ] **Step 2:** FAIL. **Step 3:** `@EnableWebSocketMessageBroker`, endpoint `/ws` with SockJS, broker `/topic`. `VaultEventBroadcaster` `@EventListener` on `FileChangeEvent` → `SimpMessagingTemplate.convertAndSend("/topic/vault", payload)`. **Step 4:** PASS. **Step 5:** Commit `feat(api): websocket broadcast of vault changes`.

---

## Phase 5 — Frontend foundation (comms, stores, layout, explorer, panes)

### Task 5.1: API client + types
**Files:** Create `frontend/src/api/types.ts`, `frontend/src/api/client.ts`; Test `client.test.ts`.
- [ ] Typed wrappers: `getTree`, `readFile`, `writeFile(path,content,baseMtime)`, `createFile`, `deleteFile`, `renameFile`, `getBacklinks`. Map 409→a typed `ConflictError` carrying server content. Tests with mocked `fetch`. Commit `feat(frontend): typed api client`.

### Task 5.2: Zustand stores
**Files:** Create `stores/vaultStore.ts`, `stores/workspaceStore.ts`, `stores/editorStore.ts`; Tests for each.
- [ ] `vaultStore`: `tree`, `loadTree()`, `applyChange(event)` (insert/remove/rename node). `workspaceStore`: pane tree (recursive: split node `{dir, a, b, size}` | leaf `{tabs:[path], active}`), `openTab`, `closeTab`, `splitActive(dir)`, `setActive`. `editorStore`: map path→`{content,dirty,baseMtime,loading}`, `load`, `setContent`, `markSaved`. Tests assert reducers. Commit `feat(frontend): vault/workspace/editor stores`.

### Task 5.3: WebSocket client
**Files:** Create `ws/wsClient.ts`; Test `wsClient.test.ts`.
- [ ] STOMP over SockJS to `/ws`, subscribe `/topic/vault`, dispatch into `vaultStore.applyChange` + notify open editors (Phase 6 `externalSync`). Auto-reconnect with backoff; on (re)connect → `vaultStore.loadTree()` + resync active files. Test connect/dispatch/reconnect with a mocked STOMP client. Commit `feat(frontend): stomp websocket client with reconnect+resync`.

### Task 5.4: Layout shell + theme
**Files:** Create `App.tsx` (replace skeleton), `components/layout/{Ribbon,Sidebar,Workspace,Splitter}.tsx`, `styles/theme.css`; Tests for Splitter + layout render.
- [ ] Three-region layout: left Ribbon (icons) + resizable Sidebar, center Workspace, right Sidebar (backlinks). `Splitter` drag-resizes; sidebars collapsible. Dark theme via CSS variables matching Obsidian's default-dark palette. Commit `feat(frontend): app shell layout + dark theme`.

### Task 5.5: File explorer (tree + CRUD + context menu)
**Files:** Create `components/explorer/{FileExplorer,TreeNode,ExplorerContextMenu}.tsx`; Tests.
- [ ] Render `vaultStore.tree`; expand/collapse folders; click file → `workspaceStore.openTab`. Context menu: New note, New folder, Rename (inline), Delete (confirm), all via api client + optimistic update reconciled by WS. Tests: render tree, open on click, create/rename/delete call client. Commit `feat(frontend): file explorer with CRUD context menu`.

### Task 5.6: Tabs + split panes
**Files:** Create `components/workspace/{PaneTree,TabBar,EditorPane}.tsx`; Tests.
- [ ] `PaneTree` recursively renders the workspace pane tree; `TabBar` per leaf with active highlight + close; split via command/menu (horizontal/vertical). `EditorPane` is a placeholder host until Phase 6. Tests: open multiple tabs, switch active, split creates two leaves, close removes tab/empty leaf. Commit `feat(frontend): tabs + split panes`.

---

## Phase 6 — Frontend editor + inline Live Preview (the hard part)

Build the Live Preview incrementally; each markdown construct is its own test+commit so regressions are isolated.

### Task 6.1: CM6 host + markdown + autosave
**Files:** Create `editor/createEditor.ts`, `editor/markdownConfig.ts`, `editor/autosave.ts`; wire into `components/workspace/EditorPane.tsx`; Tests `createEditor.test.ts`, `autosave.test.ts`.
- [ ] `createEditor(parent, {doc, onChange})` builds an `EditorView` with `markdown()`, history, dark theme, line wrapping. `EditorPane` loads file via `editorStore`, mounts editor, debounced (~400ms) `autosave` → `writeFile`, updates `baseMtime`, clears dirty; on `ConflictError` set a conflict flag. **Ordering note (prevents reload-flicker):** `autosave` must update `editorStore.baseMtime` with the value returned by `writeFile` *synchronously on the response*, so when the server's echoed `modified` WS event arrives (Task 6.8) its mtime matches `baseMtime` and the "not dirty → reload" branch is skipped for the file we just saved ourselves. Tests: doc loads; edit triggers debounced save with correct args; conflict sets flag; saved-file echo does not trigger reload. Commit `feat(frontend): codemirror host + autosave`.

### Task 6.2: Live Preview engine — cursor-aware reveal scaffold
**Files:** Create `editor/livePreview/livePreviewPlugin.ts`, `editor/livePreview/decorations.ts`; Test `decorations.test.ts`.
- [ ] **Step 1 (test):** given a doc + selection, `buildDecorations(state)` returns a `DecorationSet` that, for lines NOT containing the selection head, hides syntax marks; for the active line, leaves source visible. Start with a trivial construct (bold `**x**`) to prove the cursor-aware reveal mechanism.
- [ ] **Step 2:** FAIL. **Step 3:** Implement a `ViewPlugin` that recomputes decorations on doc/selection change by walking the Lezer markdown tree (`syntaxTree(state)`); helper `isRangeActive(sel, from, to)`. Hide marks with `Decoration.replace`, style content with `Decoration.mark`. **Step 4:** PASS. **Step 5:** Commit `feat(editor): live preview engine with cursor-aware reveal (bold)`.

### Task 6.3–6.7: Construct-by-construct (one test+commit each)
Each task: failing decoration test → implement node handler → pass → commit.
- [ ] **6.3 Headings** — `# .. ######` render styled, hash marks hidden off-cursor. `feat(editor): live preview headings`.
- [ ] **6.4 Emphasis & inline code** — bold/italic/strikethrough/inline-code mark hiding + styling. `feat(editor): live preview emphasis + inline code`.
- [ ] **6.5 Lists, quotes, HR** — bullet/number/task lists, blockquote bar, horizontal rule. `feat(editor): live preview lists/quotes/hr`.
- [ ] **6.6 Fenced code blocks** — keep monospace block, optional language label; do not hide fences when cursor inside. `feat(editor): live preview code blocks`.
- [ ] **6.7 Plain markdown links + raw URLs** — `[text](url)` renders as clickable, source on active line. `feat(editor): live preview standard links`.

### Task 6.8: External-change reconciliation
**Files:** Create `editor/externalSync.ts`; wire into `EditorPane` + `wsClient`; Test.
- [ ] On WS `modified` for an open file: if editor not dirty → re-`readFile` and `dispatch` doc replace (preserve cursor if possible); if dirty → set conflict state (banner: Keep mine / Load theirs). On `deleted` → mark tab missing. Tests for both branches. Commit `feat(editor): reconcile external file changes`.

---

## Phase 7 — Frontend linking (wikilinks, embeds, backlinks)

### Task 7.1: Wikilink widget + navigation
**Files:** Create `editor/livePreview/widgets.tsx`, `editor/wikilink/wikilinkNavigate.ts`; extend live preview; Tests.
- [ ] Render `[[Target]]` / `[[Target|Alias]]` off-cursor as a clickable link widget (alias text, "unresolved" styling if target missing). Click/Ctrl-click → resolve via tree/index → `workspaceStore.openTab` (or open in split on Ctrl). Source shows on active line. Tests: widget built for link; click opens tab; unresolved styled. Commit `feat(editor): wikilink widgets + navigation`.

### Task 7.2: Create-on-click for unresolved links
**Files:** Modify `wikilinkNavigate.ts`.
- [ ] Clicking an unresolved `[[New Note]]` → `createFile("New Note.md")` then open it. Test: unresolved click calls create then open. Commit `feat(editor): create note on unresolved wikilink click`.

### Task 7.3: Wikilink autocomplete
**Files:** Create `editor/wikilink/wikilinkAutocomplete.ts`; Test.
- [ ] CM6 autocomplete source: trigger after `[[`, complete against vault file basenames (and `#heading` after `#`, `^block` after `^`). Inserts `[[Name]]`, positions cursor. Test: completions returned for prefix; selecting inserts correctly. Commit `feat(editor): wikilink autocomplete`.

### Task 7.4: Embeds `![[ ]]`
**Files:** Modify `widgets.tsx` + live preview.
- [ ] `![[image.png]]` → `<img src=/api/attachments/...>`; `![[Note]]` → transcluded rendered note content (read-only block, fetched + markdown-rendered). Tests for image embed + note transclusion. Commit `feat(editor): embeds for images and notes`.

### Task 7.5: Backlinks panel
**Files:** Create `components/backlinks/BacklinksPanel.tsx`; wire to right sidebar + active tab; Test.
- [ ] On active note change → `getBacklinks(path)` → list grouped by source note with snippet + line; click → open source at line. Refresh when WS events touch the graph. Test: renders backlinks, click navigates. Commit `feat(frontend): backlinks panel`.

---

## Phase 8 — Integration, prod image, polish, DoD

### Task 8.1: End-to-end live sync verification
- [ ] With dev compose up: edit a vault file from the host/CLI → confirm explorer + open editor update live; rename in UI → backlinks rewritten on disk; create-on-click persists. Fix issues via @superpowers:systematic-debugging. Commit fixes.

### Task 8.2: Production multi-stage image
**Files:** Finalize `backend/Dockerfile` (stage1 `node` build frontend → `frontend/dist`; stage2 `maven` copy dist to `backend/src/main/resources/static` + build jar; stage3 `temurin:21-jre` run jar), `docker-compose.yml` (single service, vault volume, `:8080`), Spring static-resource + SPA fallback (`WebConfig` forward unknown non-`/api` routes to `index.html`).
- [ ] **Verify:** `docker compose up --build` → app served from `:8080` single container; SPA routes work; vault persists. Commit `feat: production multi-stage image serving SPA`.

### Task 8.3: Optional Playwright e2e
**Files:** `frontend/e2e/core-flow.spec.ts`.
- [ ] Open note → type → see live preview → click `[[link]]` → navigate → see backlink. Run against dev compose. Commit `test(e2e): core obsidian flow`.

### Task 8.4: README + DoD check
- [ ] Update `README.md` (architecture, run, test, vault location). Walk the M1 Definition of Done from the spec; check every item. Commit `docs: M1 readme + done checklist`.

---

## M1 Definition of Done (from spec §5)
- [ ] `docker compose -f docker-compose.dev.yml up` → working app at `:5173` showing the vault (WSL Docker).
- [ ] Create/open/edit/rename/delete notes & folders from explorer; edits autosave to disk and survive restart.
- [ ] Inline Live Preview renders markdown with cursor-aware source reveal.
- [ ] `[[wikilinks]]` autocomplete + navigate + create-on-click + alias; `![[embeds]]` render; backlinks correct.
- [ ] Tabs + split panes; external/CLI edits reflect live.
- [ ] Renaming a note rewrites its backlinks.
- [ ] `docker compose up` (prod image) serves the same app from one container.
- [ ] Backend + frontend test suites pass (run in Docker).
