# Channel History Git Plugin — Design Document

**Version:** 3.0.0
**Mirth Connect:** 4.4.1 – 26.3.0 (primary target: 4.6.1)
**Build Tool:** Maven (multi-module)
**License:** Apache 2.0

---

## 1. Purpose

A Mirth Connect plugin that provides Git-backed version control for channels, code templates, libraries, and global scripts. Users can commit and push changes to a remote Git repository (SSH or HTTPS) either manually or automatically on save.

---

## 2. Project Structure

```
Channel-History-Git-Plugin/
├── pom.xml                          # Parent POM — version management, build profiles
├── shared/                          # DTOs, interfaces, constants — used by all modules
│   └── src/main/java/com/innovarhealthcare/channelHistory/shared/
│       ├── model/                   # GitSettings, CommitMetaData, RepoItemMetadata, …
│       ├── dto/response/            # RepoFile, RepoFolder, RepoInfo, ErrorResponse, …
│       ├── util/                    # CommitMessageUtil, JsonUtils, ResponseUtil, …
│       ├── interfaces/              # VersionHistoryServletInterface (JAX-RS)
│       └── diff/                    # ObjectDiff, FieldNode, FieldType, …
├── server/                          # Business logic, Git operations, REST servlet
│   └── src/main/java/com/innovarhealthcare/channelHistory/server/
│       ├── controller/              # GitRepositoryController (singleton)
│       ├── service/                 # VersionHistoryService, GitRepositoryService
│       ├── repository/              # ChannelRepository, LibraryRepository, …
│       ├── git/                     # GitOperations, FileOperations, GitCommitterHelper
│       ├── exception/               # Custom exception hierarchy
│       └── plugin/                  # VersionHistoryPlugin, ChannelVersionPlugin, …
├── client/                          # Swing UI — dialogs, panels, task pane, tables
│   └── src/main/java/com/innovarhealthcare/channelHistory/client/
│       ├── panel/                   # VersionHistorySettingPanel, tab panels
│       ├── dialog/                  # History dialogs, comparison dialogs, import dialogs
│       ├── taskpane/                # VersionHistoryTaskPane, context classes, operations
│       ├── table/                   # Commit and repo item tables + models
│       ├── diff/                    # DiffComparisonPanel, ScriptDiffEngine, DiffLine, …
│       ├── model/                   # ChannelWithRaw, CodeTemplateWithRaw, VersionInfo
│       ├── service/                 # VersionHistoryServiceClient (REST client)
│       └── plugin/                  # VersionHistorySettingPlugin, ChannelHistoryTabPlugin, …
└── distribution/                    # Assembly descriptor → plugin ZIP
    └── assembly/zip.xml
```

---

## 3. Module Responsibilities

| Module | JAR | Responsibility |
|---|---|---|
| `shared` | `channelHistory-shared.jar` | DTOs, constants, REST interface contract, diff utilities |
| `server` | `channelHistory-server.jar` | Git operations, repositories, business logic, REST servlet |
| `client` | `channelHistory-client.jar` | Swing UI, task pane, REST client, diff display |
| `distribution` | *(zip)* | Plugin packaging — JAR assembly, `plugin.xml` generation |

---

## 4. Key Classes

### 4.1 Shared

| Class | Responsibility |
|---|---|
| `VersionControlConstants` | Static constants for property keys and entity modes (`MODE_CHANNEL`, `MODE_CODE_TEMPLATE`, etc.) |
| `GitSettings` | POJO: remote URL, branch name, auth type (`SSH`\|`HTTPS`), SSH key (inline or file path), HTTPS username/password/credentials-file path; `isSSH()` / `isHTTPS()` helpers; auth-type-aware `validate()` |
| `VersionHistoryProperties` | **Mutable** configuration loaded from Java `Properties`; supports in-place `fromProperties()` update to avoid orphaned service references |
| `CommitMetaData` | Commit hash, committer, timestamp, and message |
| `CommitMessageUtil` | Parses/formats structured commit messages: `"{Type} name: {Name}. Message: {Msg}. Server Name: {SrvName}. Server Id: {SrvId}"` |
| `VersionHistoryServletInterface` | JAX-RS interface declaring all plugin REST endpoints |
| `JsonUtils` | Jackson `ObjectMapper` wrapper |
| `ResponseUtil` | Builds standardised JSON responses (`status`, `message`, `operationDetails`) |
| `ErrorResponseFactory` | Factory producing `ErrorResponse` objects |
| `ObjectDiff` | Field-level object comparison via OGNL + Apache BeanUtils |
| `RepoFile` | DTO: `name` (String), `sizeBytes` (long); Jackson `@JsonCreator` / `@JsonProperty` |
| `RepoFolder` | DTO: `name`, `fileCount` (int), `files` (List\<RepoFile\>, defaults to empty list) |
| `RepoInfo` | DTO: `localRepoPath`, `remoteUrl`, `branch`, `totalSizeBytes` (long), `folders` (List\<RepoFolder\>, defaults to empty list) |

### 4.2 Server

| Class | Responsibility |
|---|---|
| `GitRepositoryController` | Singleton entry point; owns lifecycle (`init`, `start`, `update`, `stop`); wires together `GitRepositoryService` and `VersionHistoryService` |
| `GitRepositoryService` | Thread-safe singleton managing the JGit connection (clone / open / pull on start; factory for repository instances); `validateSSHConnection(GitSettings)` clones to a temp dir with `--no-checkout` to test reachability then deletes the dir — never throws, returns `null` on success or an error string; private `buildSshSessionFactory(GitSettings)` constructs a `JschConfigSessionFactory` from inline key bytes or a file path; `getRepoInfo()` scans the top two levels of the working tree (folders → files), skips `.git`, and returns a `RepoInfo` snapshot with path, remote URL, branch, total size (`FileUtils.sizeOfDirectory`), and folder list |
| `VersionHistoryService` | Business logic facade; coordinates repository layer for save, delete, history, and content-retrieval operations; `validateGitConnection(Properties)` parses a temporary `VersionHistoryProperties` and delegates to `GitRepositoryService.validateSSHConnection()`; `getRepoInfo()` checks `isGitAvailable()` (throws `GitNotConnectedException` if not) then delegates to `GitRepositoryService.getRepoInfo()` |
| `GitOperations` | Low-level JGit wrapper: stage, commit, push (normal + force), pull-with-overwrite, read-at-revision, file history |
| `FileOperations` | File I/O using Mirth's `ObjectXMLSerializer` for serialisation/deserialisation |
| `ChannelRepository` | Channel entity data-access over Git |
| `LibraryRepository` | Code-template library data-access |
| `CodeTemplateRepository` | Code template data-access |
| `GlobalScriptRepository` | Global scripts data-access |
| `VersionHistoryPluginServlet` | JAX-RS servlet; maps HTTP requests → `VersionHistoryService`; maps exceptions → HTTP status codes; `getRepoInfo()` serialises `RepoInfo` to JSON, maps `GitNotConnectedException` → 503 |
| `VersionHistoryPlugin` | `ServicePlugin` entry point (`init`, `start`, `stop`, `update`) |
| `ChannelVersionPlugin` | Channel-change listener → triggers auto-commit |
| `CodeTemplateVersionPlugin` | Code-template change listener → triggers auto-commit |
| `GitCommitterHelper` | Converts a Mirth `User` to a JGit `PersonIdent` |

**Exception hierarchy:**
```
GitRepositoryException
├── GitNotConnectedException    → HTTP 503
├── GitOperationException       → HTTP 500
├── GitPushFailedException      → HTTP 409
└── GitFileNotFoundException    → HTTP 404

VersionHistoryApiException      → carries HTTP status; thrown from servlet layer
```

### 4.3 Client

| Class | Responsibility |
|---|---|
| `VersionHistoryServiceClient` | REST client wrapper; calls all server endpoints including `validateSetting()` and `getRepoInfo()` (deserialises JSON response into `RepoInfo` via `JsonUtils.fromJson`) |
| `VersionHistorySettingPanel` | Top-level settings panel with four tabs: General, Git Settings, Git Behavior, Git Status; tabs 1–3 disabled when plugin is off; Git Status tab blocks navigation when there are unsaved changes |
| `GeneralTabPanel` | Plugin enable/disable toggle |
| `GitSettingsTabPanel` | Remote URL, branch, SSH key (paste-key / file-path radio toggle); "Validate Connection" button opens `GitValidationDialog` (inner class) |
| `GitBehaviorTabPanel` | Two titled sections in one tab — **Auto Commit** (enable, prompt, default message) and **Sync Delete** (auto-remove deleted entities from Git) |
| `GitStatusTabPanel` | Live repository status tab: **Repository Info** panel (local path, remote URL, branch, size) + **File Browser** panel (two-level `JTree`: top-level folders as parents, files with sizes as leaves); auto-loads via `HierarchyListener` (`SHOWING_CHANGED`) when tab becomes visible; `LoadRepoInfoWorker` (`SwingWorker`) fetches data in background; Refresh button re-triggers load; loading/error/loaded state transitions with indeterminate `JProgressBar` and red status label |
| `ChannelHistoryTabPanel` | Commit history table + content preview for a channel |
| `VersionHistoryTaskPane` | Context-sensitive task pane for channels, code templates, global scripts |
| `TaskPaneContextManager` | Manages the active `TaskPaneContext` based on current Mirth view |
| `ChannelHistoryOperations` | Channel-specific operations invoked from the task pane |
| `GlobalScriptOperations` | Global-script operations invoked from the task pane |
| `CodeTemplateOperations` | Code-template operations invoked from the task pane |
| `VersionComparisonDialog` | Side-by-side comparison of two channel revisions |
| `GlobalScriptsHistoryDialog` | History viewer and diff display for global scripts |
| `ScriptDiffEngine` | Line-based diff algorithm |
| `DiffComparisonPanel` | Renders unified diff with colour-coded ADDED / REMOVED / UNCHANGED lines |
| `CommitMetaDataTable` | Swing table displaying commit history rows |
| `ChannelRepoTable` | Swing table displaying channels present in the Git repo |

**`GitSettingsTabPanel.GitValidationDialog` (inner class)**

A modal `JDialog` opened by the "Validate Connection" button:

1. `validateFields()` runs first on the EDT — highlights invalid fields and returns early if any are blank.
2. Dialog opens with an indeterminate `JProgressBar` and "Validating Git connection…" label; Close button is disabled.
3. `ValidateWorker` (`SwingWorker`) calls `VersionHistoryServiceClient.validateSetting(toGitSettingsProperties())` on a background thread.
4. `done()` hides the progress bar and either shows a green `✓ Connection successful` label (success) or a red `✗ <server error message>` label (failure), then enables Close.
5. `DO_NOTHING_ON_CLOSE` prevents accidental dismissal while validation is in progress.

---

## 5. Architecture & Design Patterns

### 5.1 Layered Architecture

```
┌─────────────────────────────┐
│          Client (UI)        │  Swing panels, dialogs, task pane
├─────────────────────────────┤
│       REST / HTTP           │  JAX-RS servlet ↔ REST client
├─────────────────────────────┤
│      Service Layer          │  VersionHistoryService (business logic)
├─────────────────────────────┤
│     Repository Layer        │  Channel/Library/CodeTemplate/GlobalScript repos
├─────────────────────────────┤
│    Git Operations Layer     │  GitOperations (JGit), FileOperations
└─────────────────────────────┘
```

### 5.2 Patterns Used

| Pattern | Where |
|---|---|
| **Singleton** | `GitRepositoryController`, `GitRepositoryService` |
| **Factory** | `GitRepositoryService.getXxxRepository()` creates repository instances |
| **Template Method** | `BaseRepository` — common save/history logic, overridden in subclasses |
| **Observer/Listener** | `ChannelVersionPlugin`, `CodeTemplateVersionPlugin` react to Mirth events |
| **Strategy** | Different save strategies per entity type in `VersionHistoryService` |
| **Builder** | `ResponseUtil` and `ErrorResponseFactory` for response construction |
| **In-place Mutation** | `VersionHistoryProperties.fromProperties()` updates existing instance to preserve live service references |

---

## 6. Data Flow

### 6.1 Commit and Push a Channel

```
User clicks "Commit & Push" in Mirth Designer
        ↓
VersionHistoryTaskPane / ChannelHistoryTabPanel
        ↓
VersionHistoryServiceClient.commitAndPushChannel(channel, message, userId)
        ↓  HTTP POST /plugins/version-history/commitAndPushChannel
VersionHistoryPluginServlet.commitAndPushChannel()
        ↓
VersionHistoryService.saveChannelAndPush(channel, message, user)
        ↓
ChannelRepository.saveAndPush(channel, commitMessage)
        ├─ FileOperations.serialize(channel)  →  writes XML to working tree
        ├─ GitOperations.stageFiles([channelFile])
        ├─ GitOperations.commit(message, committer)
        └─ GitOperations.push(forcePush=false)
        ↓
JSON response  →  client displays success / error
```

### 6.2 View Commit History

```
User opens history panel
        ↓
VersionHistoryServiceClient.getHistory(fileName, mode)
        ↓  HTTP GET /plugins/version-history/history?fileName=…&mode=channel
VersionHistoryPluginServlet.getHistory()
        ↓
VersionHistoryService.getChannelHistory(channelId)
        ↓
ChannelRepository.getHistory(filePath)
        ↓
GitOperations.getFileHistory(filePath)  →  JGit log walk
        ↓
List<CommitMetaData>  →  JSON  →  CommitMetaDataTable rendered in UI
```

### 6.3 Retrieve Content at Revision

```
User selects a commit in history table
        ↓
VersionHistoryServiceClient.getContentAtRevision(id, revision, mode)
        ↓  HTTP GET /plugins/version-history/content?id=…&revision=…&mode=channel
VersionHistoryPluginServlet.getContentAtRevision()
        ↓
VersionHistoryService.getChannelContentAtRevision(id, revision)
        ↓
ChannelRepository.getContent(id, revision)
        ├─ GitOperations.readFileAtRevision(filePath, revision)  →  XML string
        └─ FileOperations.deserialize(xml)  →  Channel object
        ↓
XML string  →  JSON  →  VersionComparisonDialog / DiffComparisonPanel
```

### 6.4 Plugin Startup

```
Mirth Connect starts
        ↓
VersionHistoryPlugin.init(properties)
        ↓
GitRepositoryController.getInstance().init(properties)
        ↓
GitRepositoryController.start()
        ↓
GitRepositoryService.startGit()
        ├─ if .git exists  →  open repo + pull
        └─ if not          →  clone from remoteUrl
        ↓
Plugin ready; isGitAvailable() = true
```

### 6.5 Validate Git Connection

```
User clicks "Validate Connection" in GitSettingsTabPanel
        ↓
GitSettingsTabPanel.validateFields()  →  highlight blanks, return if invalid
        ↓
GitValidationDialog opens (modal, indeterminate progress bar, Close disabled)
        ↓
ValidateWorker (SwingWorker) starts on background thread
        ↓
VersionHistoryServiceClient.validateSetting(toGitSettingsProperties())
        ↓  HTTP POST /plugins/version-history/validateSetting
VersionHistoryPluginServlet.validateSetting(properties)
        ↓
VersionHistoryService.validateGitConnection(properties)
        ├─ new VersionHistoryProperties(properties)   [temporary — does not mutate live config]
        └─ GitRepositoryService.validateSSHConnection(gitSettings)
               ├─ buildSshSessionFactory(gitSettings)  [inline key bytes OR file path]
               ├─ Git.cloneRepository().setNoCheckout(true).call()
               │      → temp dir: <appData>/version-control-validate-<timestamp>/
               ├─ tempGit.close()
               └─ FileUtils.deleteDirectory(tempDir)   [always, in finally]
        ↓
null (success) → "Successfully connected…" string
error string   → returned as-is
        ↓
done() on EDT:
  success → green "✓ Connection successful", Close enabled
  failure → red "✗ <error message>",       Close enabled
```

### 6.6 Get Repository Info (Git Status Tab)

```
User selects "Git Status" tab (or tab becomes visible)
        ↓
HierarchyListener fires (SHOWING_CHANGED && isShowing())
        ↓
GitStatusTabPanel.loadData()
  ├─ loadingBar visible, Refresh disabled, values cleared to "—"
  └─ new LoadRepoInfoWorker().execute()
        ↓ (background thread)
VersionHistoryServiceClient.getRepoInfo()
        ↓  HTTP GET /plugins/version-history/repoInfo
VersionHistoryPluginServlet.getRepoInfo()
        ↓
VersionHistoryService.getRepoInfo()
  ├─ isGitAvailable() == false  →  throw GitNotConnectedException  →  HTTP 503
  └─ gitRepositoryService.getRepoInfo()
        ├─ FileUtils.sizeOfDirectory(repositoryDirectory)
        ├─ scan repositoryDirectory top-level: skip .git, collect RepoFolder per dir
        │    └─ per folder: collect RepoFile per file child (name + sizeBytes)
        └─ return RepoInfo(localRepoPath, remoteUrl, branch, totalSizeBytes, folders)
        ↓
JsonUtils.toJson(repoInfo)  →  HTTP 200
        ↓
JsonUtils.fromJson(json, RepoInfo.class)
        ↓ (EDT via done())
exitLoadedState(info):
  ├─ localRepoPathValueLabel, remoteUrlValueLabel, branchValueLabel, sizeValueLabel populated
  ├─ JTree rebuilt from folders/files, all rows expanded
  └─ loadingBar hidden, Refresh enabled
```

---

## 7. REST API

Base path: `/plugins/version-history`

| Method | Path | Parameters | Description |
|---|---|---|---|
| `GET` | `/history` | `fileName`, `mode` | Commit history for an entity |
| `GET` | `/content` | `id`, `revision`, `mode` | Entity XML at a specific commit |
| `POST` | `/validateSetting` | body: Properties | Validate Git configuration |
| `POST` | `/commitAndPushChannel` | body: Channel, `message`, `userId` | Save & push a channel |
| `POST` | `/commitAndPushCodeTemplate` | `codeTemplateId`, `message`, `userId` | Save & push a code template |
| `POST` | `/commitAndPushGlobalScripts` | body: Map\<String,String\>, `message`, `userId` | Save & push global scripts |
| `GET` | `/channel_on_repo` | — | All channel metadata from the repo |
| `GET` | `/code_template_on_repo` | — | All code template metadata from the repo |
| `GET` | `/libraries_and_templates` | — | Libraries + template metadata |
| `POST` | `/saveLibraries` | body: List\<CodeTemplateLibrary\>, `message`, `userId` | Save libraries batch |
| `GET` | `/repoInfo` | — | Local repo path, remote URL, branch, size, and two-level file tree |

**HTTP error codes:**

| Code | Condition |
|---|---|
| 400 | Validation failed / bad input |
| 404 | File not found at revision |
| 409 | Push rejected (conflict) |
| 503 | Git not connected |
| 500 | Unhandled Git operation failure |

---

## 8. Configuration Properties

| Key | Default | Description |
|---|---|---|
| `versionHistory.enable` | `false` | Master enable switch |
| `versionHistory.auto.commit.enable` | `false` | Auto-commit on channel/template save |
| `versionHistory.auto.commit.prompt` | `false` | Prompt user for message before auto-commit |
| `versionHistory.auto.commit.message` | `""` | Default auto-commit message |
| `versionHistory.syncDelete` | `false` | Commit a deletion when entity is deleted in Mirth |
| `versionHistory.remote.url` | `""` | Remote Git URL (SSH or HTTPS) |
| `versionHistory.remote.branch` | `""` | Branch name |
| `versionHistory.remote.ssh.key` | `""` | SSH private key content (inline) |
| `versionHistory.remote.ssh.keyPath` | `""` | Path to SSH private key file on server |
| `versionHistory.remote.authType` | `"SSH"` | Authentication type: `SSH` or `HTTPS` (default `SSH` for backward compatibility) |
| `versionHistory.remote.https.username` | `""` | HTTPS username (direct input) |
| `versionHistory.remote.https.password` | `""` | HTTPS password or PAT (direct input) |
| `versionHistory.remote.https.credentialsPath` | `""` | Path to credentials file on server (`username:password`) |

---

## 9. Commit Message Format

```
{ObjectType} name: {ObjectName}. Message: {UserMessage}. Server Name: {ServerName}. Server Id: {ServerId}
```

Example:
```
Channel name: PatientDataChannel. Message: Fixed validation logic. Server Name: Production. Server Id: 123e4567-e89b-12d3-a456-426614174000
```

`CommitMessageUtil` provides static methods to extract each field; backward-compatible with the older format that omitted `Server Name`.

---

## 10. Build & Deployment

**Build profiles** (pass `-P <profile>` to Maven):

| Profile | Mirth Version | Java |
|---|---|---|
| *(default)* | 4.6.1 | 17 |
| `v441` – `v453` | 4.4.1 – 4.5.3 | 8–17 |
| `v460` | 4.6.0 | 17 |
| `v2630` | 26.3.0 | 17 |

**Output artifact** (`distribution` module):
```
innovarhealthcare-channel-history-v3.0.0-bl4.6.1.zip
├── channelHistory-server.jar
├── channelHistory-shared.jar
├── channelHistory-client.jar
├── plugin.xml
└── libs/
    ├── jgit-*.jar
    ├── jackson-*.jar
    ├── objmeld-*.jar
    ├── ognl-*.jar
    └── java-diff-utils-*.jar
```

**Key runtime dependencies:**

| Library | Purpose |
|---|---|
| JGit 5.13.5 | Git operations |
| JSch (via JGit) | SSH transport |
| Jackson 2.14.3 | JSON serialisation |
| Apache Commons (Lang3, Collections4, IO, BeanUtils) | Utilities |
| OGNL 3.2.15 | Object field introspection |
| ObjMeld 3.4.0 | Object graph comparison |
| Java-diff-utils 4.12 | Line diff generation |

---

## 11. Thread Safety Notes

- `GitRepositoryService` — all public methods are `synchronized`.
- `GitRepositoryController` — singleton creation is `synchronized`.
- `VersionHistoryService` — **not** internally synchronised; callers (the servlet) are responsible.
- `VersionHistoryProperties` — mutable; concurrent updates to configuration during plugin `update()` could cause a brief inconsistency window.

---

## 12. Known Limitations

- `GitRepositoryController.isGitConnected()` currently returns a hardcoded value; live status detection is not fully implemented.
- SSH strict host key checking is disabled by default — acceptable for internal use but a security consideration for public deployments.
- No merge conflict resolution: conflicting pushes are rejected with HTTP 409 and must be resolved manually.
- `VersionHistoryService` is not thread-safe at the service level; high-concurrency environments could see race conditions on simultaneous saves.
- Sensitive fields (SSH private key, HTTPS password) are stored as plain text in properties. A future task will implement XStream-based encryption/masking to prevent credentials from appearing in logs and exports.
- `validateSSHConnection()` only exercises the SSH (and default) transport path. HTTPS credential validation (`authType = "HTTPS"`) is not yet wired — the method will attempt a clone using the default session factory and likely fail with an authentication error rather than providing a meaningful message.
