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
│       ├── dto/response/            # RepoFile, RepoFolder, RepoInfo, RepoChanges, ErrorResponse, …
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
│       ├── dialog/                  # History dialogs, diff dialogs, import dialogs
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
| `RepoChanges` | DTO: `modifiedFiles` (List\<String\>, from `Status.getModified()`), `deletedFiles` (List\<String\>, from `Status.getRemoved()` + `Status.getMissing()`), `untrackedFiles` (List\<String\>, from `Status.getUntracked()`); all default to empty list; Jackson `@JsonCreator` / `@JsonProperty` |

### 4.2 Server

| Class | Responsibility |
|---|---|
| `GitRepositoryController` | Singleton entry point; owns lifecycle (`init`, `start`, `update`, `stop`); wires together `GitRepositoryService` and `VersionHistoryService` |
| `GitRepositoryService` | Thread-safe singleton managing the JGit connection; `validateSSHConnection(GitSettings)` clones to a temp dir with `--no-checkout` to test reachability then deletes the dir; `getRepoInfo()` scans the top two levels of the working tree (skips `.git`), returns a `RepoInfo` snapshot; `getRepoChanges()` calls `GitOperations.getRepoChanges()`, wraps `GitAPIException` as `GitOperationException`; `getFileContent(filePath)` reads raw bytes from the working tree via `FileOperations.readFileContent()`, throws `GitFileNotFoundException` if absent; `getFileContentAtHead(filePath)` reads via `GitOperations.readFileAtRevision(filePath, "HEAD")`, converts bytes to UTF-8 string |
| `VersionHistoryService` | Business logic facade; all methods guard with `isGitAvailable()` and throw `GitNotConnectedException` when Git is unavailable; `validateGitConnection(Properties)` parses a temporary `VersionHistoryProperties` and delegates to `GitRepositoryService.validateSSHConnection()`; `getRepoInfo()`, `getRepoChanges()`, `getFileContent(filePath)`, `getFileContentAtHead(filePath)` delegate directly to `GitRepositoryService` |
| `GitOperations` | Low-level JGit wrapper: stage, commit, push (normal + force), pull-with-overwrite, read-at-revision, file history; `getRepoChanges()` calls `git.status().call()` and partitions results into modified, deleted, and untracked lists |
| `FileOperations` | File I/O using Mirth's `ObjectXMLSerializer` for serialisation/deserialisation; `readFileContent(relativePath)` reads the working-tree file as a UTF-8 string using `Files.readString()` |
| `ChannelRepository` | Channel entity data-access over Git |
| `LibraryRepository` | Code-template library data-access |
| `CodeTemplateRepository` | Code template data-access |
| `GlobalScriptRepository` | Global scripts data-access |
| `VersionHistoryPluginServlet` | JAX-RS servlet; maps HTTP requests → `VersionHistoryService`; maps exceptions → HTTP status codes; `getRepoInfo()` serialises `RepoInfo` to JSON; `getRepoChanges()` serialises `RepoChanges` to JSON; `getFileContent(filePath)` returns raw file content, maps `GitFileNotFoundException` → 404; `getFileContentAtHead(filePath)` returns HEAD content, maps `GitFileNotFoundException` → 404 and `GitOperationException` → 500 |
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
| `VersionHistoryServiceClient` | REST client wrapper; `getRepoInfo()` deserialises JSON → `RepoInfo`; `getRepoChanges()` deserialises JSON → `RepoChanges`; `getFileContent(filePath)` returns raw file content string; `getFileContentAtHead(filePath)` returns HEAD content string; all methods follow the `rethrowParsedClientError` pattern |
| `VersionHistorySettingPanel` | Top-level settings panel with four tabs: General, Git Settings, Git Behavior, Git Status; tabs 1–3 disabled when plugin is off; Git Status tab blocks navigation when there are unsaved changes |
| `GeneralTabPanel` | Plugin enable/disable toggle |
| `GitSettingsTabPanel` | Remote URL, branch, SSH key (paste-key / file-path radio toggle); "Validate Connection" button opens `GitValidationDialog` (inner class) |
| `GitBehaviorTabPanel` | Two titled sections — **Auto Commit** (enable, prompt, default message) and **Sync Delete** (auto-remove deleted entities from Git) |
| `GitStatusTabPanel` | Live repository status tab: **Repository Info** panel (local path, remote URL, branch, size as four labeled rows); **JSplitPane** (50/50 horizontal split) — left: **File Browser** two-level `JTree` (folder nodes → file leaf nodes storing `FileNode{displayText, relativePath}`); right: **Working Tree Changes** `JTree` with colour-coded `[M]`/`[D]`/`[U]` prefixes via `ChangesCellRenderer`; auto-loads via `HierarchyListener`; `LoadDataWorker` (`SwingWorker`) fetches `getRepoInfo()` + `getRepoChanges()` in parallel on the background thread; double-click on file tree → `getFileContent()` then binary check → simple text viewer; double-click on changes tree → fetch content(s) → binary check → XML check → `ChannelDiffDialog` / `CodeTemplateDiffDialog` or simple text viewer |
| `ChannelHistoryTabPanel` | Commit history table + content preview for a channel |
| `VersionHistoryTaskPane` | Context-sensitive task pane for channels, code templates, global scripts |
| `TaskPaneContextManager` | Manages the active `TaskPaneContext` based on current Mirth view |
| `ChannelHistoryOperations` | Channel-specific operations invoked from the task pane |
| `GlobalScriptOperations` | Global-script operations invoked from the task pane |
| `CodeTemplateOperations` | Code-template operations invoked from the task pane |
| `VersionComparisonDialog` | Side-by-side comparison of two channel revisions |
| `GlobalScriptsHistoryDialog` | History viewer and diff display for global scripts; contains `GlobalScriptsDiffPanel` |
| `ChannelDiffDialog` | Modal `JDialog` (1200×800) for channel diff; `JTabbedPane` with **XML Diff** tab (`DiffComparisonPanel`) and **Channel** tab (TBD visual view); constructor auto-sorts versions (current always right, newer timestamp right); ESC closes |
| `CodeTemplateDiffDialog` | Same structure as `ChannelDiffDialog`; title "Code Template Diff"; second tab labelled "Code Template" (TBD) |
| `DiffComparisonPanel` | Generic `public` side-by-side diff panel; constructor `(VersionInfo leftVersion, VersionInfo rightVersion)`; `updateDiff(leftText, rightText)` triggers diff render; uses `ScriptDiffEngine` + `DiffTextPane`; synchronized scrolling wired in constructor |
| `ScriptDiffEngine` | Line-based diff using java-diff-utils; produces `DiffResult` with left/right `DiffLine` lists annotated with `ADDED`/`DELETED`/`UNCHANGED` |
| `CommitMetaDataTable` | Swing table displaying commit history rows |
| `ChannelRepoTable` | Swing table displaying channels present in the Git repo |

**`GitSettingsTabPanel.GitValidationDialog` (inner class)**

A modal `JDialog` opened by the "Validate Connection" button:

1. `validateFields()` runs first on the EDT — highlights invalid fields and returns early if any are blank.
2. Dialog opens with an indeterminate `JProgressBar` and "Validating Git connection…" label; Close button is disabled.
3. `ValidateWorker` (`SwingWorker`) calls `VersionHistoryServiceClient.validateSetting(toGitSettingsProperties())` on a background thread.
4. `done()` hides the progress bar and either shows a green `✓ Connection successful` label (success) or a red `✗ <server error message>` label (failure), then enables Close.
5. `DO_NOTHING_ON_CLOSE` prevents accidental dismissal while validation is in progress.

**`GitStatusTabPanel` — double-click file-open logic:**

| Source | Content type | Action |
|---|---|---|
| File Browser (any file) | Binary (contains `\0` in first 8 KB) | Error dialog: "Cannot display binary file: {name}" |
| File Browser (any file) | Text | Simple read-only viewer (800×600 `JDialog`, monospaced `JTextArea`) |
| Changes `[M]` | Binary | Error dialog |
| Changes `[M]` | Text, not XML | Simple viewer (current content) |
| Changes `[M]` | XML | `ChannelDiffDialog` or `CodeTemplateDiffDialog` — HEAD left, Current right |
| Changes `[D]` | Binary | Error dialog |
| Changes `[D]` | Text, not XML | Simple viewer (HEAD content) |
| Changes `[D]` | XML | Diff dialog — HEAD left, blank "Deleted" right |
| Changes `[U]` | Binary | Error dialog |
| Changes `[U]` | Text, not XML | Simple viewer (current content) |
| Changes `[U]` | XML | Diff dialog — blank "Not in repository" left, current right |

Path routing: path containing `codetemplate`/`libraries`/`library` (case-insensitive) → `CodeTemplateDiffDialog`; otherwise → `ChannelDiffDialog`.

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
| **Builder** | `ResponseUtil`, `ErrorResponseFactory`, and `VersionInfo.Builder` |
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

### 6.6 Load Git Status Tab

```
User selects "Git Status" tab (or tab becomes visible)
        ↓
HierarchyListener fires (SHOWING_CHANGED && isShowing())
        ↓
GitStatusTabPanel.loadData()
  ├─ loadingBar visible, Refresh disabled, all values cleared to "—"
  └─ new LoadDataWorker().execute()
        ↓ (background thread — both calls sequential)
VersionHistoryServiceClient.getRepoInfo()
        ↓  HTTP GET /plugins/version-history/repoInfo
VersionHistoryService.getRepoInfo()  →  GitRepositoryService.getRepoInfo()
        ├─ FileUtils.sizeOfDirectory(repositoryDirectory)
        ├─ scan top-level dirs (skip .git): build RepoFolder per dir
        │    └─ per folder: build RepoFile per file (name + sizeBytes)
        └─ return RepoInfo(localRepoPath, remoteUrl, branch, totalSizeBytes, folders)

VersionHistoryServiceClient.getRepoChanges()
        ↓  HTTP GET /plugins/version-history/repoChanges
VersionHistoryService.getRepoChanges()  →  GitRepositoryService.getRepoChanges()
        └─ GitOperations.getRepoChanges()
               ├─ git.status().call()
               ├─ modifiedFiles  = status.getModified()
               ├─ deletedFiles   = status.getRemoved() + status.getMissing()
               └─ untrackedFiles = status.getUntracked()

        ↓ (EDT via done())
exitLoadedState(info, changes):
  ├─ Repository Info panel: localRepoPath, remoteUrl, branch, size populated
  ├─ File Browser JTree rebuilt from RepoInfo.folders/files (all rows expanded)
  │    └─ leaf nodes store FileNode{displayText, relativePath} for double-click lookup
  └─ Working Tree Changes JTree rebuilt:
       ├─ "Changed (N)" group: [M] modifiedFiles + [D] deletedFiles (ChangesCellRenderer colours)
       └─ "Unversioned Files (N)" group: [U] untrackedFiles
```

### 6.7 Open File Content (double-click in Git Status Tab)

```
User double-clicks a file node in File Browser or Changes JTree
        ↓
SwingWorker starts; setCursor(WAIT_CURSOR)
        ↓ (background thread)
  File Browser node:
    VersionHistoryServiceClient.getFileContent(relativePath)
        ↓  HTTP GET /plugins/version-history/fileContent?filePath=…
        ↓  FileOperations.readFileContent(relativePath)  →  UTF-8 string

  [M] node:
    getFileContentAtHead(filePath)  +  getFileContent(filePath)
        ↓  GET /fileContentAtHead  +  GET /fileContent

  [D] node:
    getFileContentAtHead(filePath)
        ↓  GET /fileContentAtHead

  [U] node:
    getFileContent(filePath)
        ↓  GET /fileContent

        ↓ (EDT via done()); setCursor(DEFAULT_CURSOR)
Binary check (scan first 8 KB for '\0'):
  → true   → JOptionPane error: "Cannot display binary file: {name}"
  → false  → text content:
       not XML (trimmed doesn't start with '<'):
         → showTextViewer(title, content)  [800×600 modal JDialog, monospaced JTextArea]
       XML:
         → path routing:  codetemplate / libraries / library  →  CodeTemplateDiffDialog
                          otherwise                            →  ChannelDiffDialog
         → VersionInfo built per side (isCurrent=true for "Current" / "Deleted")
         → dialog.setVisible(true)
```

### 6.8 Diff Dialog Version Auto-Sort

```
ChannelDiffDialog / CodeTemplateDiffDialog constructor
        ↓
sortVersions(version1, version2, xml1, xml2)
  ├─ version1.isCurrent() && !version2.isCurrent()  →  version1 goes RIGHT
  ├─ version2.isCurrent() && !version1.isCurrent()  →  version2 goes RIGHT
  ├─ both not current: version1.timestamp.after(version2.timestamp) →  version1 goes RIGHT
  └─ default: keep original order
        ↓
DiffComparisonPanel(leftVersion, rightVersion)
diffPanel.updateDiff(leftXml, rightXml)
        ↓
ScriptDiffEngine.diff(leftLines, rightLines)  →  DiffResult
DiffTextPane (left) + DiffTextPane (right) rendered with colour-coded lines
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
| `GET` | `/repoChanges` | — | Working tree changes: modified, deleted, and untracked file lists |
| `GET` | `/fileContent` | `filePath` | Raw file content from working tree (UTF-8); 404 if not found |
| `GET` | `/fileContentAtHead` | `filePath` | Raw file content at HEAD revision (UTF-8); 404 if not found at HEAD |

**HTTP error codes:**

| Code | Condition |
|---|---|
| 400 | Validation failed / bad input |
| 404 | File not found at revision or in working tree |
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

- **Exception handling inconsistency** — The service layer (`GitRepositoryService`, `VersionHistoryService`) mixes checked and unchecked exceptions across methods without a uniform policy. Some methods declare checked exceptions in their signatures; others throw unchecked `GitOperationException` silently. Needs a full audit and standardisation against the `GitRepositoryException` hierarchy.
- **Sensitive fields stored as plain text** — SSH private key content and HTTPS password are stored as plain text in the plugin properties file. A future task will implement XStream-based encryption/masking to prevent credentials appearing in logs and exports.
- **HTTPS validation not yet implemented** — `validateSSHConnection()` only exercises the SSH transport path. When `authType = "HTTPS"`, the method attempts a clone with the default session factory and will likely fail with a generic authentication error rather than a meaningful message.
- **`DiffComparisonPanel` — no synchronized scrolling** — The left and right `DiffTextPane` scroll panes are not linked; scrolling one side does not scroll the other. `setupSynchronizedScrolling()` is called in the constructor but the implementation is not yet complete.
- **`DiffComparisonPanel` — no blank-line padding for alignment** — Deleted lines on the left have no corresponding blank placeholder on the right (and vice versa), so matching context lines fall out of vertical alignment when diffs are large.
- **`ChannelDiffDialog` / `CodeTemplateDiffDialog` — visual view TBD** — The second tab ("Channel" / "Code Template") shows a placeholder `JLabel("TBD")`. A structured visual comparison (rendered channel properties, connector list, etc.) is not yet implemented.
- **`GitRepositoryController.isGitConnected()` stub** — Currently returns a hardcoded value; live status detection is not fully implemented.
- **SSH strict host key checking disabled** — Acceptable for internal use but a security consideration for public deployments.
- **No merge conflict resolution** — Conflicting pushes are rejected with HTTP 409 and must be resolved manually outside the plugin.
