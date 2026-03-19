# CLAUDE.md — Developer Guide for Claude Code

This file provides context and conventions for working on the Channel History Git Plugin.

---

## 1. Project Overview

A Mirth Connect plugin (v4.4.1–26.3.0) that adds Git-backed version control for channels, code templates, libraries, and global scripts. Users commit/push to a remote Git repository (SSH or HTTPS) manually or automatically on save. The client side is a Swing UI (panels, dialogs, task pane) that talks to a JAX-RS servlet on the server via a generated REST client. Git operations are performed with JGit on the Mirth server process. The plugin ships as a ZIP containing three JARs (`server`, `shared`, `client`) plus `plugin.xml` and runtime dependency JARs.

---

## 2. Coding Conventions

### General Java Style
- Java 17 for default/modern Mirth profiles; Java 8 compatibility for `v441`–`v453` profiles — **do not use lambdas or streams in shared/server code unless certain the profile is Java 17+**. Client code targets Java 17.
- No Lombok — all POJOs use explicit constructors, getters, setters.
- Logger: `LogManager.getLogger(ClassName.class)` from Log4j 2. Field is `private static Logger logger`.
- Constants: `public static final` in dedicated `*Constants` classes or as class-level static finals.

### Error Handling
- Server code throws typed exceptions from the hierarchy: `GitNotConnectedException` (runtime, 503), `GitFileNotFoundException` (runtime, 404), `GitPushFailedException` (checked, 409), `GitOperationException` (checked, 500).
- The servlet catches each type and maps it to `VersionHistoryApiException` with the appropriate HTTP status.
- Client code wraps all REST calls through `rethrowParsedClientError()` which parses the `ErrorResponse` JSON and throws `VersionHistoryClientException`.
- **Do not** silently swallow exceptions in the service or repository layers — log and rethrow.

### REST Endpoints
- All endpoints declared in `VersionHistoryServletInterface` with `@MirthOperation` annotations.
- Add matching `@Override` in `VersionHistoryPluginServlet`.
- Add matching method in `VersionHistoryServiceClient`.
- Parameters use `@QueryParam` / `@Param` pairing per existing patterns.

### Patterns to Follow
- **New repository type:** extend `BaseRepository<T>`, override the six abstract methods, register a factory getter in `GitRepositoryService`.
- **New REST endpoint:** add to interface → servlet → service client (in that order).
- **New SwingWorker:** always call `setCursor(WAIT_CURSOR)` before `execute()`, restore `DEFAULT_CURSOR` in `done()`.
- **New dialog:** follow `ChannelDiffDialog` pattern — `setSize(1200, 800)`, `setLocationRelativeTo(parent)`, `DISPOSE_ON_CLOSE`, ESC keyboard action.

---

## 3. UI Conventions

### Layout
- **MigLayout** everywhere. Common patterns:
  - Panel fill: `new MigLayout("insets 12, novisualpadding, hidemode 3, fill")`
  - Two-column form: `"[right]12[grow,fill]"` for label+field rows
  - Action button rows: `"insets 0 10 10 10, novisualpadding, fill, gap 6"`
  - `"w 108!"` for fixed-width action buttons
- Use `BorderLayout` only inside diff/comparison panels where explicit north/center/south zones are needed.
- Use `GridLayout(1, 2, 1, 0)` for side-by-side split pane content inside `DiffComparisonPanel`.

### Mirth UI Integration
- Extend `MirthDialog` (not `JDialog`) for dialogs that should integrate with Mirth's look and feel.
  - **Exception:** `ChannelDiffDialog` and `CodeTemplateDiffDialog` use plain `JDialog` (with `Frame parent` parameter) because they are launched from contexts where `MirthDialog` is not appropriate.
- Use `UIConstants.BACKGROUND_COLOR` for panel backgrounds.
- Use `PlatformUI.MIRTH_FRAME` to get the top-level Mirth frame when needed.
- Use `PlatformUI.MIRTH_FRAME.alertInformation(component, msg)` and `.alertError(component, msg)` for user notifications (not `JOptionPane` directly, unless inside a non-Mirth context).
- `Frame parent = PlatformUI.MIRTH_FRAME` is the standard field in history dialogs.

### SwingWorker Pattern
```java
// Before starting:
setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
new MyWorker().execute();

// In done():
setCursor(Cursor.getDefaultCursor());
try {
    Result r = get();
    // update UI
} catch (ExecutionException e) {
    logger.error("...", e);
    showError("...");
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

### Color Coding in Diff Views
- `[M]` Modified:   `new Color(204, 102, 0)` / `#CC6600` (orange)
- `[A]` Added:      `new Color(0, 153, 0)`   / `#009900` (green)
- `[D]` Deleted:    `new Color(204, 0, 0)`   / `#CC0000` (red)
- `[U]` Unversioned: same green as `[A]`

These values are defined in `GitStatusTabPanel.ChangesCellRenderer` and must match `FunctionListCellRenderer`.

---

## 4. Known Gotchas

### Substance LAF Overrides `setForeground()` on List Cell Renderers
Mirth Connect uses a Substance-based Look and Feel that overrides the foreground color set by `DefaultListCellRenderer.setForeground()` during cell painting. The result: colors display as black.

**Fix:** Use HTML text in the label instead:
```java
// WRONG — Substance ignores this:
setForeground(new Color(204, 102, 0));
setText("[M] " + name);

// CORRECT — HTML renderer bypasses Substance:
setText("<html><font color='#CC6600'>[M]</font> " + name + "</html>");
// When selected, use plain text so LAF selection color applies:
if (isSelected) setText("[M] " + name);
```

**Status per renderer:**
- `FunctionListCellRenderer` — fix already applied. **Do NOT revert to `setForeground()`.**
- `ScriptListCellRenderer` — fix not yet applied; currently shows black text (see Backlog).

### `VersionHistoryProperties` Must Be Updated In-Place
Never do this:
```java
// WRONG — replaces the live instance; GitRepositoryService still holds old reference:
versionHistoryProperties = new VersionHistoryProperties(newProperties);
```
Always do this:
```java
// CORRECT — mutates the existing instance that all services already reference:
versionHistoryProperties.fromProperties(newProperties);
```
This is the only way `GitRepositoryController.update()` correctly propagates config changes to `GitRepositoryService` and `VersionHistoryService` without restarting them.

### `GitOperations` is Instance-Bound — Unusable Before `startGit()`
`GitOperations` is constructed by `GitRepositoryService.startGit()`. All server-side service methods guard with `isGitAvailable()` before using it. If you add a new service method, always check `isGitAvailable()` first and throw `GitNotConnectedException` if false.

### `VersionHistoryService` Is Not Thread-Safe
`GitRepositoryService` is `synchronized` on all public methods. `VersionHistoryService` is not. Don't add mutable state to `VersionHistoryService` without also synchronizing it.

### `validateGitConnection()` Must Not Mutate Live Config
When validating new settings, always construct a **temporary** `VersionHistoryProperties` from the submitted `Properties` object. Never call `fromProperties()` on the live instance during validation — that would corrupt the running configuration if the user cancels.

### `CommitMessageUtil.extractServerId()` Validates UUID Format
If the extracted server ID is not a valid UUID, it returns a hardcoded default value rather than the raw string. Don't assume this method always returns what was committed.

### `BaseRepository.saveAndPush()` Does a Pull Before Commit
The flow is: write file → `pullWithOverwrite()` → stage → commit → push. The pull happens *after* the file is written to disk. If the pull fails, the local file is already written but the commit/push are aborted. This is intentional (optimistic concurrency) but means local files can be out of sync with Git after a failed push.

### `JXTaskPane` in `CodeTemplateHistoryDialogWithTaskPane`
`JXTaskPane` is a SwingX component provided by Mirth's runtime. Actions added to a `JXTaskPane` via `actionsPane.add(action)` render as clickable hyperlink-style labels, not buttons. This is the Mirth-style side action panel pattern.

---

## 5. Architecture Rules

| Rule | Rationale |
|---|---|
| Client never imports server classes | Standard plugin module isolation |
| Client never imports `shared.diff.*` | That package is vestigial (ObjectDiff/ObjMeld removed); nothing in it should be used |
| `VersionHistoryServiceClient` is the only place that calls REST endpoints | Single point for error handling and response parsing |
| All SwingWorker `doInBackground()` methods must not touch UI components | EDT safety |
| `GitRepositoryService` methods must be `synchronized` | Single JGit `Git` instance, not thread-safe |
| Repository subclasses must not call `gitOperations` directly | Use BaseRepository's `saveAndPush` / `deleteAndPush` / `getContent`; override hooks instead |
| New DTOs in `shared.dto.response` must use Jackson `@JsonCreator` + `@JsonProperty` constructors | Required for deserialization across the REST boundary |
| Exception mapping belongs in `VersionHistoryPluginServlet` only | Service/repository layers should throw typed exceptions, not build HTTP responses |

---

## 6. Current Backlog / TODO Items

| Item | Location | Status |
|---|---|---|
| `ChannelDiffDialog` Tab 2 "Channel" | `ChannelDiffDialog.setupLayout()` | Placeholder `JLabel("TBD")` — structured visual view not implemented |
| `ScriptListCellRenderer` Substance LAF fix | `client/diff/ScriptListCellRenderer.java` | Still uses `setForeground()` — needs HTML color migration |
| HTTPS connection validation | `GitRepositoryService.validateSSHConnection()` | Method name is a misnomer; HTTPS path not tested |
| `BaseRepository.postCommit()` | `server/repository/BaseRepository.java` | Hook exists but is a no-op in all subclasses; intended for per-channel commit ID tracking |
| `GitRepositoryController.isGitConnected()` | `server/controller/GitRepositoryController.java` | Returns hardcoded value; live detection not implemented |
| Sensitive field encryption | `GitSettings` SSH key + HTTPS password fields | Stored as plain text in plugin properties |
| `DiffComparisonPanel` blank-line padding | `client/diff/DiffComparisonPanel.java` | No filler lines for alignment between deleted/added blocks |
| Exception handling audit | Server module across service/repository/servlet layers | Mix of checked/unchecked; needs standardization against `GitRepositoryException` hierarchy |

---

## 7. Exception Handling

### Current State (as-built)

**Server — exception types:**
- `GitNotConnectedException` (runtime) — thrown by `VersionHistoryService` when `!isGitAvailable()`
- `GitFileNotFoundException` (runtime) — thrown by `GitRepositoryService` / `FileOperations` when file absent
- `GitPushFailedException` (checked) — thrown by `GitOperations.push()` on rejection
- `GitOperationException` (checked) — thrown by `GitOperations` for commit/fetch/pull failures
- `VersionHistoryApiException` (WebApplicationException) — thrown only in `VersionHistoryPluginServlet`; carries HTTP status + JSON body

**Servlet mapping:**
```
GitNotConnectedException   → 503
GitFileNotFoundException   → 404
GitPushFailedException     → 409
GitOperationException      → 500
null/blank parameters      → 400
```

**Client — error handling:**
All methods in `VersionHistoryServiceClient` call `rethrowParsedClientError(e, showAlert)` which:
1. Attempts to parse `e.getMessage()` as `ErrorResponse` JSON
2. If parseable → throws `VersionHistoryClientException(error)`
3. If not → throws original `ClientException`

### Known Inconsistencies
- Some server methods declare checked `GitOperationException` in their signature; others throw it unchecked without declaration.
- `GitRepositoryService.startGit()` swallows all exceptions (by design — fail-safe startup). Other methods do not — inconsistent exception boundary.
- `GlobalScriptRepository.deserializeAndVerify()` throws `IllegalArgumentException` for invalid map content, which does not map to any typed HTTP code.

### Planned Standardization
All `GitRepositoryService` and repository public methods should declare only `GitRepositoryException` (or its subtypes) as checked exceptions. Servlet catches the hierarchy and maps to HTTP codes. Runtime exceptions (`GitNotConnectedException`, `GitFileNotFoundException`) continue as unchecked since they represent programmer-visible state mismatches.
