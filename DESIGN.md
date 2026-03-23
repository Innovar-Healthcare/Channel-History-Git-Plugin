# Channel History Git Plugin — Design Document

**Version:** 3.0.0
**Mirth Connect:** 4.4.1 – 26.3.0 (primary target: 4.6.1)
**Build Tool:** Maven (multi-module)
**License:** Apache 2.0

---

## 1. Purpose

A Mirth Connect plugin that provides Git-backed version control for channels, code templates, libraries, and global scripts. Users can commit and push changes to a remote Git repository (SSH or HTTPS) either manually or automatically on save. The plugin also exposes a Git Status view showing live repo state, working-tree changes, and file-level diff dialogs.

---

## 2. Project Structure

```
Channel-History-Git-Plugin/
├── pom.xml                          # Parent POM — version management, build profiles
├── shared/                          # DTOs, interfaces, constants — used by all modules
│   └── src/main/java/com/innovarhealthcare/channelHistory/shared/
│       ├── model/                   # GitSettings, CommitMetaData, VersionHistoryProperties, …
│       ├── dto/response/            # RepoFile, RepoFolder, RepoInfo, RepoChanges,
│       │                            #   LibraryMetadata, LibrariesAndTemplatesResponse,
│       │                            #   RepoItemMetadata, ErrorResponse
│       ├── util/                    # CommitMessageUtil, JsonUtils, ErrorResponseFactory
│       ├── interfaces/              # VersionHistoryServletInterface (JAX-RS)
│       └── diff/                    # (unused — was ObjectDiff/FieldNode; now empty)
├── server/                          # Business logic, Git operations, REST servlet
│   └── src/main/java/com/innovarhealthcare/channelHistory/server/
│       ├── controller/              # GitRepositoryController (singleton)
│       ├── service/                 # VersionHistoryService, GitRepositoryService
│       ├── repository/              # Repository<T>, BaseRepository<T>,
│       │                            #   ChannelRepository, LibraryRepository,
│       │                            #   CodeTemplateRepository, GlobalScriptRepository
│       ├── git/                     # GitOperations, FileOperations, GitCommitterHelper
│       ├── exception/               # GitRepositoryException hierarchy,
│       │                            #   VersionHistoryApiException
│       └── plugin/                  # VersionHistoryPlugin, ChannelVersionPlugin,
│                                    #   CodeTemplateVersionPlugin
├── client/                          # Swing UI — dialogs, panels, task pane, tables
│   └── src/main/java/com/innovarhealthcare/channelHistory/client/
│       ├── panel/                   # VersionHistorySettingPanel, tab panels,
│       │   │                        #   ChannelHistoryTabPanel
│       │   └── gitstatus/           # FilesTabPanel, ChangesTabPanel,
│       │                            #   HistoryTabPanel
│       ├── dialog/                  # History dialogs, diff dialogs, import dialogs
│       ├── taskpane/                # VersionHistoryTaskPane, contexts, operations
│       ├── table/                   # CommitMetaDataTable, repo item tables + models
│       ├── diff/                    # DiffComparisonPanel, ScriptDiffEngine,
│       │                            #   CodeTemplateFunctionParser, renderers, models
│       ├── model/                   # ChannelWithRaw, CodeTemplateWithRaw,
│       │                            #   CommitMetaDataTableModel, …
│       ├── service/                 # VersionHistoryServiceClient (REST client)
│       ├── exception/               # VersionHistoryClientException
│       ├── util/                    # VersionControlUtil
│       └── plugin/                  # VersionHistorySettingPlugin,
│                                    #   ChannelHistoryTabPlugin, VersionHistoryTaskPlugin
└── distribution/                    # Assembly descriptor → plugin ZIP
    └── assembly/zip.xml
```

---

## 3. Module Responsibilities

| Module | JAR | Responsibility |
|---|---|---|
| `shared` | `channelHistory-shared.jar` | DTOs, constants, REST interface contract, commit message utilities |
| `server` | `channelHistory-server.jar` | Git operations, repositories, business logic, REST servlet |
| `client` | `channelHistory-client.jar` | Swing UI, task pane, REST client, diff display |
| `distribution` | *(zip)* | Plugin packaging — JAR assembly, `plugin.xml` generation |

---

## 4. Key Classes

### 4.1 Shared

| Class | Responsibility |
|---|---|
| `VersionControlConstants` | Static property key constants (`VERSION_HISTORY_ENABLE`, etc.) and entity mode constants (`MODE_CHANNEL`, `MODE_CODE_TEMPLATE`, `MODE_CODE_TEMPLATE_LIBRARY`, `MODE_GLOBAL_SCRIPTS`) |
| `GitSettings` | POJO: `remoteRepositoryUrl`, `branchName`, `sshPrivateKey`, `sshPrivateKeyPath`, `authType`, `httpsUsername`, `httpsPassword`, `httpsCredentialsPath`; `isSSH()` / `isHTTPS()` helpers; auth-type-aware `validate()` |
| `VersionHistoryProperties` | **Mutable** configuration loaded from Java `Properties`; `fromProperties(Properties)` updates all fields **in-place** (preserves live service references — never replaced); `toProperties()` converts back |
| `CommitMetaData` | Commit hash, committer, timestamp (long ms), message; `getShortHash()`, `getMessageContent()`, `getServerId()`, `getServerName()` |
| `VersionHistoryErrorCodes` | Static error code string constants (`INVALID_REQUEST`, `GIT_NOT_CONNECTED`, `GIT_AUTH_FAILED`, `PUSH_REJECTED`, `FILE_NOT_FOUND`, etc.) |
| `CommitMessageUtil` | Parses and formats structured commit messages: `"{Type} name: {Name}. Message: {Msg}. Server Name: {SrvName}. Server Id: {SrvId}"`; static extraction methods for each field; backward-compatible with old format omitting Server Name; inner `BatchLibraries` wrapper for batch library commits |
| `JsonUtils` | Static Jackson `ObjectMapper` (ISO 8601 dates, unknown properties ignored); `fromJson`, `toJson`, `toJsonPretty`, `fromJsonList` |
| `ErrorResponseFactory` | `build(code, message)` → `ErrorResponse` with UTC timestamp |
| `VersionHistoryServletInterface` | JAX-RS interface declaring all plugin REST endpoints with `@MirthOperation` metadata |
| `RepoFile` | DTO: `name`, `sizeBytes` |
| `RepoFolder` | DTO: `name`, `fileCount`, `files` (List\<RepoFile\>, defaults to empty) |
| `RepoInfo` | DTO: `localRepoPath`, `remoteUrl`, `branch`, `totalSizeBytes`, `folders` (List\<RepoFolder\>) |
| `RepoChanges` | DTO: `modifiedFiles` (from `Status.getModified()`), `deletedFiles` (from `Status.getRemoved()` + `Status.getMissing()`), `untrackedFiles` (from `Status.getUntracked()`); all List\<String\>, default empty |
| `RepoItemChange` | DTO: `path` (String), `changeType` (String — `"MODIFIED"` \| `"ADDED"` \| `"DELETED"`); `@JsonCreator` + `@JsonProperty`; `equals`/`hashCode` via `EqualsBuilder`/`HashCodeBuilder(17,37)` |
| `RepoItemMetadata` | DTO: `id`, `name`, `path`, `lastCommitId`; equals/hashCode via Apache Commons builders |
| `LibraryMetadata` | DTO: `id`, `name`, `codeTemplateIds` (List\<String\>) |
| `LibrariesAndTemplatesResponse` | DTO: `libraries` (List\<LibraryMetadata\>), `templates` (List\<RepoItemMetadata\>) |
| `ErrorResponse` | DTO: `status` ("error"), `code`, `message`, `timestamp` (UTC ISO 8601) |

### 4.2 Server

| Class | Responsibility |
|---|---|
| `GitRepositoryController` | Singleton entry point; `init(Properties)`, `start()`, `update(Properties)`, `stop()`; wires `GitRepositoryService` and `VersionHistoryService`; `update()` calls `VersionHistoryProperties.fromProperties()` then `GitRepositoryService.startGit()` |
| `GitRepositoryService` | Thread-safe singleton managing JGit lifecycle; `startGit()` — clones or opens repo, sets `gitAvailable`; never throws (swallows failures into `gitUnavailableReason`); `getRepoInfo()` scans top two directory levels (skips `.git`); `getRepoChanges()` delegates to GitOperations; `getFileContent(filePath)` via FileOperations; `getFileContentAtHead(filePath)` via GitOperations; `validateSSHConnection(GitSettings)` clones to temp dir (`--no-checkout`) then deletes |
| `VersionHistoryService` | **Non-thread-safe** business logic facade; all mutating methods guard with `isGitAvailable()` → `GitNotConnectedException`; `saveChannelAndPush`, `saveCodeTemplateAndPush`, `saveLibrariesAndPush`, `saveGlobalScriptsAndPush`; `writeChannelToRepo(channel)`, `writeCodeTemplateToRepo(ct)` — write file to working tree only (no commit); `deleteChannelFromRepo(channel)`, `deleteCodeTemplateFromRepo(ct)` — delete file from working tree only (no commit); history and content-at-revision queries; `validateGitConnection(Properties)` creates a temporary `VersionHistoryProperties` without mutating the live one |
| `GitOperations` | Low-level JGit wrapper; constructed with `(Git, branch, SshSessionFactory)`; `readFileAtRevision(path, revision)` → byte[]; `getRepoChanges()` → RepoChanges via `git.status().call()`; `getFileHistory(path)` → List\<CommitMetaData\>; `stageFiles`, `commit`, `push(forcePush)`; `pullWithOverwrite()` — fetch + hard reset to remote; `hasRemoteChanges()` |
| `FileOperations` | File I/O via Mirth's `ObjectXMLSerializer`; `writeXml`, `readXml`, `deserializeXml`; `readFileContent(relativePath)` — UTF-8 string from working tree via `Files.readString()`; `listFiles`, `deleteFile`, `fileExists` |
| `BaseRepository<T>` | Abstract template: `save()`, `saveAndPush()` (save → pull-with-overwrite → commit → push), `load()`, `delete()`, `deleteAndPush()`, `loadMetadata()`, `getHistory()`, `getContent()`; subclasses override `extractId`, `extractName`, `getEntityClass`, `deserializeAndVerify`, `generateFilename`, `postCommit` |
| `ChannelRepository` | Extends BaseRepository\<Channel\>; directory `"channels"` |
| `CodeTemplateRepository` | Extends BaseRepository\<CodeTemplate\>; directory `"codetemplates"` |
| `LibraryRepository` | Extends BaseRepository\<CodeTemplateLibrary\>; directory `"libraries"`; extra `saveAllAndPush(List)` for batch operations; `buildBatchCommitMessage` truncates library names at 200 chars |
| `GlobalScriptRepository` | Extends BaseRepository\<Map\<String,String\>\>; directory `"globalscripts"`, filename `"scripts"`; validates map contains only known types (`Deploy`, `Undeploy`, `Preprocessor`, `Postprocessor`) |
| `GitCommitterHelper` | Static `fromUser(User)` and `fromUser(User, domain)` → JGit `PersonIdent` |
| `VersionHistoryPluginServlet` | JAX-RS servlet; maps requests → VersionHistoryService; maps exceptions → HTTP codes (see below); `getRepoInfo()` / `getRepoChanges()` serialize DTOs to JSON; `getFileContent` / `getFileContentAtHead` return raw strings |
| `VersionHistoryPlugin` | `ServicePlugin` entry point: `init`, `start`, `stop`, `update`; `getDefaultProperties()` |
| `ChannelVersionPlugin` | Channel-save event listener → if `autoCommit = true`: commit + push; if `autoCommit = false`: write file to working tree only; `remove()` respects same logic for sync-delete |
| `CodeTemplateVersionPlugin` | Code-template-save event listener → same logic as `ChannelVersionPlugin`; `remove(CodeTemplateLibrary)` is empty |

**Exception hierarchy:**
```
GitRepositoryException (checked)
├── GitOperationException   (checked)  → HTTP 500
└── GitPushFailedException  (checked)  → HTTP 409

GitNotConnectedException    (runtime)  → HTTP 503
GitFileNotFoundException    (runtime)  → HTTP 404

VersionHistoryApiException  (WebApplicationException) — carries HTTP status + ErrorResponse JSON body
```

### 4.3 Client

| Class | Responsibility |
|---|---|
| `VersionHistoryServiceClient` | Singleton REST client; wraps every call in `rethrowParsedClientError()` which parses `ErrorResponse` JSON and throws `VersionHistoryClientException`; methods mirror every server endpoint |
| `VersionHistoryClientException` | Extends `ClientException`; holds `ErrorResponse getError()` |
| `VersionControlUtil` | Static helpers: `isEnableVersionControl(Client)`, `isAutoCommitEnable(Client)`, `getChannelCommitId`, `setChannelCommitId`, `getAlertText()` |
| `ChannelWithRaw` | Pair of `Channel` + `rawContent` (String) |
| `CodeTemplateWithRaw` | Pair of `CodeTemplate` + `rawContent` (String) |
| `CommitMetaDataTableModel` | TableModel wrapping `List<CommitMetaData>` |
| `VersionHistorySettingPanel` | Top-level settings panel; four tabs: General, Git Settings, Git Behavior, Git Status; tabs 1–3 disabled when plugin off; Git Status tab blocks navigation on unsaved changes |
| `GeneralTabPanel` | Plugin enable/disable toggle |
| `GitSettingsTabPanel` | Remote URL, branch, SSH key (paste / file-path radio toggle); "Validate Connection" → `GitValidationDialog` (inner class); inner dialog: progress bar + `ValidateWorker` SwingWorker; Close disabled during validation; success = green `✓`, failure = red `✗ <message>` |
| `GitBehaviorTabPanel` | Auto Commit section (enable, prompt, default message) + Sync Delete section |
| `GitStatusTabPanel` | Shell panel (~230 lines); owns header bar (4-field repo info strip: local path, remote URL, branch, size), `JTabbedPane` with 3 tabs (Files, Changes, History), and `LoadDataWorker`; `LoadDataWorker` fetches `getRepoInfo()` + `getRepoChanges()` in parallel via `CompletableFuture`; delegates all tab logic to 3 sub-panels via `onTabSelected()`; `dataLoaded` flag prevents redundant reloads on re-entry; `reset()` clears state after save/refresh |
| `FilesTabPanel` | Files tab (under `gitstatus/`); owns file browser `JTree`, `FILE_INFO` card, `EMPTY` card; `onTabSelected()`; `populate(RepoInfo)`; \[View Full History\] button callback → `GitStatusTabPanel.onViewFullHistory(relativePath)` |
| `ChangesTabPanel` | Changes tab (under `gitstatus/`); owns changes `JTree` (`ChangesCellRenderer`), embedded `DiffComparisonPanel`, `EMPTY` card; `onTabSelected()`; `populate(RepoChanges)`; single-click selection loads inline diff |
| `HistoryTabPanel` | History tab (under `gitstatus/`); owns `JList<CommitMetaData>` (`CommitListCellRenderer` with HTML colors), `JList<RepoItemChange>` (changed files), embedded `DiffComparisonPanel`, filter label, Clear filter button, `JProgressBar`; `onTabSelected()` → `loadRepoLog()`; `loadHistory(relativePath)` for file-filtered view; `setModel()` for batch list updates (Java 8 compatible, fires single event); all API calls are file-path-based (not Mirth entity ID) |
| `ChannelHistoryTabPanel` | Channel history tab; commit table + XML preview; diff buttons open `ChannelDiffDialog` |
| `VersionHistoryTaskPane` | Context-sensitive task pane (channels, code templates, global scripts) |
| `TaskPaneContextManager` | Manages active `TaskPaneContext` |
| `ChannelHistoryOperations` | Channel task-pane operations |
| `CodeTemplateOperations` | Code-template task-pane operations |
| `GlobalScriptOperations` | Global-script task-pane operations |
| `ChannelDiffDialog` | Modal `JDialog` (1200×800); `JTabbedPane`: **XML Diff** (`DiffComparisonPanel`) + **Channel** (`ChannelDiffPanel`); auto-sorts versions (current always right, newer timestamp right); ESC closes |
| `CodeTemplateDiffDialog` | Modal `JDialog` (1200×800); `JTabbedPane`: **XML Diff** (`DiffComparisonPanel`) + **Code Template** (`CodeTemplateFunctionDiffPanel`); same sort/ESC behaviour; opened by `CodeTemplateHistoryDialog`, `CodeTemplateHistoryDialogWithTaskPane`, `GitStatusTabPanel` |
| `CodeTemplateHistoryDialog` | Code-template history: commit table; "Diff" → current vs. selected (`showDiffLastChangeWindow`); right-click "Show Diff" → two selected revisions (`showDiffWindow`); both paths open `CodeTemplateDiffDialog` |
| `CodeTemplateHistoryDialogWithTaskPane` | Alternate code-template history with `JXTaskPane` action panel; Diff action supports 1-row (vs. current) or 2-row (vs. each other); opens `CodeTemplateDiffDialog` |
| `GlobalScriptsHistoryDialog` | Global scripts history + `GlobalScriptsDiffPanel` |
| `DiffComparisonPanel` | Generic `public` diff panel; `DiffComparisonPanel(VersionInfo left, VersionInfo right)`; `updateDiff(leftText, rightText)` renders both split and unified; **▲ Prev / ▼ Next** navigation buttons jump between change blocks (consecutive ADDED/DELETED runs); on `updateDiff()` auto-scrolls to first change block and enables/disables buttons accordingly (both disabled when no changes); navIndex resets per `updateDiff()` and on Split/Unified toggle; split mode scrolls left pane only (sync listener propagates to right); `modelToView()` called inside double `invokeLater` to ensure layout is complete; **Split/Unified toggle** (CardLayout: `"SPLIT"` GridLayout 1×2 / `"UNIFIED"` single pane); synchronized vertical scrolling |
| `ScriptDiffEngine` | Line-based diff via java-diff-utils; `computeDiff(leftText, rightText)` → `DiffResult` (left+right `DiffLine` lists for split view); `computeUnifiedDiff(leftText, rightText)` → `List<DiffLine>` (interleaved DELETED/ADDED/UNCHANGED for unified view) |
| `DiffTextPane` | `JTextPane` subclass rendering `List<DiffLine>` with colour-coded backgrounds |
| `DiffLine` | Fields: `lineNumber`, `content`, `changeType` (ChangeType enum) |
| `DiffResult` | Pair of `leftLines` / `rightLines` (List\<DiffLine\>) for split view |
| `ChangeType` | Enum: `ADDED`, `DELETED`, `UNCHANGED` |
| `VersionInfo` | Version metadata DTO; Builder pattern; `name`, `version`, `author`, `timestamp` (Date), `isCurrent`; factory methods `createCurrent(name, author)` and `createHistorical(name, hash, author, timestamp)` |
| `ScriptEntry` | DTO: `name`, `leftCode`, `rightCode`, `changeType`; `toString()` returns `name` (used as default JList display) |
| `CodeTemplateFunctionParser` | Parses JavaScript functions from Mirth code-template XML `<code>` element; strips `<![CDATA[…]]>`; handles `function name(…)` and `Object.method = function(…)` forms; brace-counting body extraction aware of `//`, `/* */`, `"`, `'`, `` ` `` literals and `\\` escape; returns `LinkedHashMap<String, String>` in source order; advances past each body to skip nested functions |
| `CodeTemplateFunctionDiffPanel` | "Code Template" tab panel; `JSplitPane` (left: `JList<ScriptEntry>`, divider=250; right: `DiffComparisonPanel` re-created per selection via `setRightComponent`); `buildChangedEntries()` unions function names, skips UNCHANGED; ADDED: left code/version=""; DELETED: right code/version="Deleted"; auto-selects index 0 |
| `FunctionListCellRenderer` | Cell renderer for `CodeTemplateFunctionDiffPanel` list; HTML colour coding (`<font color='#CC6600'>` [M], `<font color='#009900'>` [A], `<font color='#CC0000'>` [D]) to bypass Substance LAF `setForeground()` override; plain text when selected |
| `ScriptListCellRenderer` | Cell renderer for `GlobalScriptsDiffPanel` list; uses `setForeground()` (has Substance LAF color issue — not yet migrated to HTML) |
| `ChannelComponentParser` | Parses Mirth channel XML into structural components for diff; uses `DocumentBuilderFactory` with XXE-prevention feature flags; **Channel Info**: direct-child `<name>`, `<description>`, `<revision>` formatted as labelled text block; **Source**: direct-child `<sourceConnector>` → `ChannelConnector(transportName, xmlContent)`; **Destinations**: direct children of `<destinationConnectors>` keyed by connector `<name>`, display label from `<transportName>`; `directChildText()` avoids picking up nested `<name>` elements from connectors |
| `ChannelConnector` | Package-private DTO: `transportName` (String), `xmlContent` (String) |
| `ChannelComponents` | Package-private container: `channelInfo` (String), `sourceConnector` (ChannelConnector), `destinations` (LinkedHashMap\<String, ChannelConnector\> keyed by connector name) |
| `ChannelEntry` | Package-private JList item DTO: `label`, `changeType`, `leftContent`, `rightContent`, `leftVersionOverride`, `rightVersionOverride`; null overrides mean use outer dialog VersionInfo unchanged |
| `ChannelEntryListCellRenderer` | Package-private cell renderer for `ChannelDiffPanel` component list; HTML colour coding for [M]/[A]/[D] (same hex constants as `FunctionListCellRenderer`) to bypass Substance LAF; unchanged items shown as muted gray (`#777777`) HTML text; plain text when selected |
| `ChannelDiffPanel` | "Channel" tab panel for `ChannelDiffDialog`; `JSplitPane` (divider=250, resizeWeight=0.2): left — `JList<ChannelEntry>` showing **all** components (Channel Info always present, Source Connector, each Destination) with [M]/[A]/[D]/unchanged indicators; right — `DiffComparisonPanel` re-created per selection; **Channel Info**: always shown, [M] if differs; **Source**: same-transportName=[M]/unchanged, different-transportName=[D]+[A]; **Destinations**: matched by connector `<name>` — [M]/unchanged/[A]/[D]; auto-selects first changed entry or index 0 if all unchanged |
| `GlobalScriptsDiffPanel` | Global scripts diff: `JList` of script types on left, `DiffComparisonPanel` on right; similar structure to `CodeTemplateFunctionDiffPanel` |
| `CommitMetaDataTable` | JTable displaying commit history |
| `ChannelRepoTable` | JTable for channels in repository |

**`GitSettingsTabPanel.GitValidationDialog` (inner class)**

1. `validateFields()` runs on EDT — highlights blanks, returns early if invalid.
2. Opens with indeterminate `JProgressBar`, "Validating…" label, Close disabled.
3. `ValidateWorker` (SwingWorker) calls `VersionHistoryServiceClient.validateSetting()` on background thread.
4. `done()` hides bar; shows green `✓ Connection successful` or red `✗ <error>`, enables Close.
5. `DO_NOTHING_ON_CLOSE` prevents dismissal during validation.

**`GitStatusTabPanel` — double-click file-open logic:**

| Source | Content type | Action |
|---|---|---|
| File Browser (any) | Binary (contains `\0` in first 8 KB) | Error dialog: "Cannot display binary file: {name}" |
| File Browser (any) | Text | Read-only viewer (800×600 `JDialog`, monospaced `JTextArea`) |
| Changes `[M]` | Binary | Error dialog |
| Changes `[M]` | Text, not XML | Viewer (current content) |
| Changes `[M]` | XML | `ChannelDiffDialog` or `CodeTemplateDiffDialog` — HEAD left, current right |
| Changes `[D]` | Binary | Error dialog |
| Changes `[D]` | Text, not XML | Viewer (HEAD content) |
| Changes `[D]` | XML | Diff dialog — HEAD left, blank "Deleted" right |
| Changes `[U]` | Binary | Error dialog |
| Changes `[U]` | Text, not XML | Viewer (current content) |
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
| **Singleton** | `GitRepositoryController`, `GitRepositoryService`, `VersionHistoryServiceClient` |
| **Factory** | `GitRepositoryService.getXxxRepository()` creates repository instances |
| **Template Method** | `BaseRepository<T>` — common save/history logic; subclasses override `extractId`, `deserializeAndVerify`, `postCommit`, etc. |
| **Observer / Listener** | `ChannelVersionPlugin`, `CodeTemplateVersionPlugin` react to Mirth events |
| **Strategy** | Different repository implementations per entity type |
| **Builder** | `ErrorResponseFactory`, `VersionInfo.Builder`, `RepoInfo`/`RepoChanges` Jackson constructors |
| **In-place Mutation** | `VersionHistoryProperties.fromProperties()` — updates existing instance to preserve live service references |
| **Fail-Safe Startup** | `GitRepositoryService.startGit()` swallows all exceptions into `gitUnavailableReason`; plugin stays alive, all operations return HTTP 503 |

---

## 6. Data Flow

### 6.1 Commit and Push a Channel

```
User clicks "Commit & Push"
        ↓
VersionHistoryTaskPane / ChannelHistoryTabPanel
        ↓
VersionHistoryServiceClient.commitAndPushChannel(channel, message, userId)
        ↓  HTTP POST /plugins/version-history/commitAndPushChannel
VersionHistoryPluginServlet.commitAndPushChannel()
        ↓
VersionHistoryService.saveChannelAndPush(channel, message, user)
        ↓
ChannelRepository.saveAndPush(channel, commitMessage, committer, forcePush=false)
        ├─ FileOperations.writeXml("channels", channelId, channel)
        ├─ GitOperations.pullWithOverwrite()   ← pull before commit
        ├─ GitOperations.stageFiles([channelFile])
        ├─ GitOperations.commit(message, committer)
        └─ GitOperations.push(forcePush=false)
        ↓
JSON response  →  client displays success / error
```

### 6.2 View Commit History

```
User opens history panel / tab
        ↓
VersionHistoryServiceClient.loadChannelHistory(channelId)
        ↓  HTTP GET /plugins/version-history/history?fileName=…&mode=channel
VersionHistoryPluginServlet.getHistory()
        ↓
VersionHistoryService.getChannelHistory(channelId)
        ↓
ChannelRepository.getHistory(id)
        ↓
GitOperations.getFileHistory(filePath)  →  JGit log walk (newest first)
        ↓
List<CommitMetaData>  →  JSON  →  CommitMetaDataTable rendered in UI
```

### 6.3 Retrieve Content at Revision

```
User selects commit in history table
        ↓
VersionHistoryServiceClient.loadChannelWithRawFromRepo(channelId, revision)
        ↓  HTTP GET /plugins/version-history/content?id=…&revision=…&mode=channel
VersionHistoryPluginServlet.getContentAtRevision()
        ↓
VersionHistoryService.getChannelContentAtRevision(id, revision)
        ↓
ChannelRepository.getContent(id, revision)
        ├─ GitOperations.readFileAtRevision(filePath, revision)  →  byte[]
        └─ FileOperations.deserializeXml(xml, Channel.class)
        ↓
XML string  →  JSON  →  ChannelDiffDialog (channels) / CodeTemplateDiffDialog (code templates)
```

### 6.4 Plugin Startup

```
Mirth Connect starts
        ↓
VersionHistoryPlugin.init(properties)
        ↓
GitRepositoryController.getInstance().init(properties)
        ├─ new GitRepositoryService(versionHistoryProperties)
        └─ new VersionHistoryService(gitRepoService, versionHistoryProperties)
        ↓
GitRepositoryController.start()
        ↓
GitRepositoryService.startGit()
        ├─ if .git exists  →  open repo + pull
        └─ if not          →  clone from remoteUrl
        ↓
Plugin ready; isGitAvailable() = true
  OR  Git startup failed; isGitAvailable() = false; all API calls return HTTP 503
```

### 6.5 Validate Git Connection

```
User clicks "Validate Connection" in GitSettingsTabPanel
        ↓
GitSettingsTabPanel.validateFields()  →  highlight blanks; return if invalid
        ↓
GitValidationDialog opens (modal, progress bar, Close disabled)
        ↓
ValidateWorker (SwingWorker) on background thread:
VersionHistoryServiceClient.validateSetting(toGitSettingsProperties())
        ↓  HTTP POST /plugins/version-history/validateSetting
VersionHistoryService.validateGitConnection(properties)
        ├─ new VersionHistoryProperties(properties)   [temporary — does NOT mutate live config]
        └─ GitRepositoryService.validateSSHConnection(gitSettings)
               ├─ buildSshSessionFactory(gitSettings)
               ├─ Git.cloneRepository().setNoCheckout(true).call()
               │      → temp dir: <appData>/version-control-validate-<timestamp>/
               ├─ tempGit.close()
               └─ FileUtils.deleteDirectory(tempDir)  [always, in finally]
        ↓
null (success) / error string  →  done() on EDT:
  success → green "✓ Connection successful", Close enabled
  failure → red "✗ <error>",                Close enabled
```

### 6.6 Load Git Status Tab

```
User selects "Git Status" tab (tab becomes visible)
        ↓
HierarchyListener fires (SHOWING_CHANGED && isShowing())
        ↓
GitStatusTabPanel.loadData()
  ├─ loadingBar visible, Refresh disabled, values cleared to "—"
  └─ new LoadDataWorker().execute()
        ↓ (background thread — sequential)
VersionHistoryServiceClient.getRepoInfo()
        ↓  HTTP GET /repoInfo
GitRepositoryService.getRepoInfo()
        ├─ FileUtils.sizeOfDirectory(repositoryDirectory)
        ├─ scan top-level dirs (skip .git): RepoFolder per dir
        │    └─ per folder: RepoFile per file (name + sizeBytes)
        └─ RepoInfo(localRepoPath, remoteUrl, branch, totalSizeBytes, folders)

VersionHistoryServiceClient.getRepoChanges()
        ↓  HTTP GET /repoChanges
GitOperations.getRepoChanges()
        ├─ git.status().call()
        ├─ modifiedFiles  = status.getModified()
        ├─ deletedFiles   = status.getRemoved() ∪ status.getMissing()
        └─ untrackedFiles = status.getUntracked()

        ↓ (EDT via done())
exitLoadedState(info, changes):
  ├─ Repository Info: 4 label rows populated
  ├─ File Browser JTree rebuilt from RepoInfo.folders/files (all rows expanded)
  │    └─ leaf nodes store FileNode{displayText, relativePath}
  └─ Changes JTree rebuilt:
       ├─ "Changed (N)": [M] modified + [D] deleted (ChangesCellRenderer)
       └─ "Unversioned Files (N)": [U] untracked
```

### 6.7 Open File Content (double-click in Git Status Tab)

```
User double-clicks a node
        ↓
SwingWorker starts; setCursor(WAIT_CURSOR)
        ↓ (background thread)
  File Browser:    getFileContent(relativePath)
  [M] node:        getFileContentAtHead(path)  +  getFileContent(path)
  [D] node:        getFileContentAtHead(path)
  [U] node:        getFileContent(path)

        ↓ (EDT via done()); setCursor(DEFAULT_CURSOR)
Binary check (scan first 8 KB for '\0'):
  → true   → JOptionPane error: "Cannot display binary file: {name}"
  → false  → XML check (trimmed content starts with '<'):
       not XML → showTextViewer(title, content)
               [800×600 modal JDialog, monospaced JTextArea, ESC closes]
       XML     → path routing → ChannelDiffDialog or CodeTemplateDiffDialog
               → VersionInfo built per case (isCurrent for working tree)
               → dialog.setVisible(true)
```

### 6.8 Diff Dialog Version Auto-Sort

```
ChannelDiffDialog / CodeTemplateDiffDialog constructor
        ↓
sortVersions(version1, version2, xml1, xml2):
  ├─ version1.isCurrent() && !version2.isCurrent()  → version1 goes RIGHT
  ├─ version2.isCurrent() && !version1.isCurrent()  → version2 goes RIGHT
  ├─ both not current: version1.timestamp.after(v2) → version1 goes RIGHT
  └─ default: keep original order
        ↓
DiffComparisonPanel(leftVersion, rightVersion)
diffPanel.updateDiff(leftXml, rightXml)
  ├─ ScriptDiffEngine.computeDiff()    → DiffResult  →  left/right DiffTextPane
  └─ ScriptDiffEngine.computeUnifiedDiff() → List<DiffLine> → unifiedPane
```

---

### 6.9 Save Channel/CodeTemplate with autoCommit = false

```
User saves channel in Mirth UI
        ↓
ChannelVersionPlugin.save()
  ├─ isGitAvailable() = false → return (no-op)
  ├─ isAutoCommitEnabled() = false
  │    └─ VersionHistoryService.writeChannelToRepo(channel)
  │         └─ ChannelRepository.save(channel)
  │              └─ FileOperations.writeXml("channels", channelId, channel)
  │    → file appears as [M] or [U] in Git Status Changes tab
  └─ isAutoCommitEnabled() = true
       └─ VersionHistoryService.saveChannelAndPush(...)  ← existing flow (see 6.1)
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
| `GET` | `/repoInfo` | — | Repo path, remote URL, branch, size, two-level file tree |
| `GET` | `/repoChanges` | — | Working tree changes: modified, deleted, untracked lists |
| `GET` | `/fileContent` | `filePath` | Raw file content from working tree (UTF-8); 404 if absent |
| `GET` | `/fileContentAtHead` | `filePath` | Raw file content at HEAD revision; 404 if absent at HEAD |
| `GET` | `/repoLog` | `maxCount` | Repo-wide commit log, newest first |
| `GET` | `/commitChanges` | `commitHash` | Files changed in a specific commit |
| `GET` | `/fileHistory` | `filePath` | Git commit history for a specific file path |
| `GET` | `/fileContentAtRevision` | `filePath`, `commitHash` | Raw file content at a specific commit |

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
| `versionHistory.remote.authType` | `"SSH"` | Authentication type: `SSH` or `HTTPS` |
| `versionHistory.remote.https.username` | `""` | HTTPS username |
| `versionHistory.remote.https.password` | `""` | HTTPS password or PAT |
| `versionHistory.remote.https.credentialsPath` | `""` | Path to `username:password` credentials file |

---

## 9. Commit Message Format

```
{ObjectType} name: {ObjectName}. Message: {UserMessage}. Server Name: {ServerName}. Server Id: {ServerId}
```

Example:
```
Channel name: PatientDataChannel. Message: Fixed validation logic. Server Name: Production. Server Id: 123e4567-e89b-12d3-a456-426614174000
```

`CommitMessageUtil` provides static extraction methods for each field. Backward-compatible with older format that omitted `Server Name`. Server Id is validated as UUID; falls back to a default if invalid. The inner `BatchLibraries` class handles batch library commits by joining names with commas, truncated at 200 characters.

---

## 10. Build & Deployment

**Build profiles** (pass `-P <profile>` to Maven):

| Profile | Mirth Version | Java |
|---|---|---|
| *(default)* | 4.6.1 | 17 |
| `v441` – `v453` | 4.4.1 – 4.5.3 | 8–17 |
| `v460` | 4.6.0 | 17 |
| `v2630` | 26.3.0 | 17 |

**Output artifact:**
```
innovarhealthcare-channel-history-v3.0.0-bl4.6.1.zip
├── channelHistory-server.jar
├── channelHistory-shared.jar
├── channelHistory-client.jar
├── plugin.xml
└── libs/
    ├── org.eclipse.jgit-5.13.5.*.jar
    ├── org.eclipse.jgit.ssh.jsch-5.13.5.*.jar
    ├── jackson-databind-2.14.3.jar + jackson-core + jackson-annotations
    ├── java-diff-utils-4.12.jar
    └── (commons-lang3, commons-io, commons-beanutils — provided by Mirth)
```

**Key runtime dependencies:**

| Library | Purpose |
|---|---|
| JGit 5.13.5 | Git operations |
| JSch (via JGit SSH transport) | SSH transport |
| Jackson 2.14.3 | JSON serialisation |
| java-diff-utils 4.12 | Line diff generation |
| Apache Commons (Lang3, Collections4, IO, BeanUtils) | Utilities (provided by Mirth) |
| MigLayout | Swing layout manager (provided by Mirth) |
| SwingX (`JXTaskPane`) | Extended Swing components (provided by Mirth) |

**Plugin registration** (`plugin.xml`):

- Client classes: `VersionHistorySettingPlugin`, `ChannelHistoryTabPlugin`, `VersionHistoryTaskPlugin`
- Server classes: `VersionHistoryPlugin`, `CodeTemplateVersionPlugin`, `ChannelVersionPlugin`
- API providers: `VersionHistoryServletInterface` (SERVLET_INTERFACE), `VersionHistoryPluginServlet` (SERVER_CLASS)

---

## 11. Thread Safety Notes

- `GitRepositoryService` — all public methods are `synchronized`.
- `GitRepositoryController` — singleton creation is `synchronized`.
- `VersionHistoryService` — **not** internally synchronized; relies on the servlet container serializing requests or on `GitRepositoryService`'s own synchronization.
- `VersionHistoryProperties` — mutable; `fromProperties()` is called from `GitRepositoryController.update()` on the Mirth plugin update thread. If a request arrives concurrently during update, a brief inconsistency window exists.
- All Swing UI updates (EDT safety): `LoadDataWorker`, `ValidateWorker`, all other SwingWorker subclasses post results via `done()` which runs on the EDT.

---

## 12. Known Limitations & Future Tasks

- **Exception handling inconsistency** — The service/repository layer mixes checked (`GitOperationException`, `GitPushFailedException`) and unchecked (`GitNotConnectedException`, `GitFileNotFoundException`) exceptions inconsistently across methods. Needs a full audit and standardization.

- **Sensitive fields stored as plain text** — SSH private key and HTTPS password are stored unencrypted in plugin properties. A future task: XStream-based encryption to prevent credentials appearing in logs/exports.

- **HTTPS connection validation not fully implemented** — `validateSSHConnection()` only tests the SSH transport path. For `authType = "HTTPS"`, the validation clone will likely fail with a generic error rather than a meaningful message.

- **`ScriptListCellRenderer` — Substance LAF color issue** — Uses `setForeground()` which Mirth Connect's Substance LAF overrides during cell painting, causing colors to display as black. `FunctionListCellRenderer` already fixed this via HTML text (`<font color='#XXXXXX'>`). `ScriptListCellRenderer` needs the same treatment.

- **`DiffComparisonPanel` — scroll sync uses `!isAdjusting` guard** — Left/right panes sync via `AdjustmentListener` with `!e.getValueIsAdjusting()`. The opposite pane only snaps into position on final scroll event (mouse release), not during live drag. Can feel laggy on large diffs.

- **`DiffComparisonPanel` — no blank-line padding** — Deleted lines on the left have no corresponding blank line on the right (and vice versa), so matching context lines fall out of vertical alignment on large diffs.

- **`GitRepositoryController.isGitConnected()` stub** — Returns a hardcoded value; live status detection not fully implemented.

- **SSH strict host key checking disabled** — Acceptable for internal deployments; a security consideration for public-facing repositories.

- **No merge conflict resolution** — Conflicting pushes are rejected with HTTP 409 and must be resolved manually outside the plugin.

- **`BaseRepository.postCommit()` stub** — The hook is called after commit but before push; currently a no-op in all subclasses. Intended for tracking per-channel last commit ID in Mirth, but the implementation is commented out.
