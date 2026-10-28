---
phase: quick-261028-jgg
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/java/com/evision/releasemgmt/adapters/briefing/FileBriefingReadAdapter.java
  - src/test/java/com/evision/releasemgmt/adapters/briefing/FileBriefingReadAdapterTest.java
  - src/test/java/com/evision/releasemgmt/adapters/web/BriefingFlowIT.java
  - briefings/README.md
  - docs/briefings-flow.md
  - docs/docker.md
autonomous: true
requirements: [QUICK-261028-jgg]

must_haves:
  truths:
    - "A task's runs come from every briefings/data/<taskId>-<UTC timestamp>.json file, newest file names first, at most 60, returned sorted by run_at descending"
    - "No matching files (or no data directory) means not run yet: empty runs, readError false"
    - "A malformed, non-object, oversized or symlinked run file is skipped and logged (task id + exception type only) while the other runs still show"
    - "A data directory that exists but cannot be listed gives readError true"
    - "A file of one task (e.g. sprint_check-...json) is never read for another task, and the old <taskId>.json array file is ignored"
  artifacts:
    - path: "src/main/java/com/evision/releasemgmt/adapters/briefing/FileBriefingReadAdapter.java"
      provides: "Per-run file listing + bounded reads"
    - path: "briefings/README.md"
      provides: "Data contract for dated per-run files, writer-side pruning"
  key_links:
    - from: "FileBriefingReadAdapter.feed"
      to: "BriefingFiles.Reader (readBounded)"
      via: "reader.read(props.dir(), fileName) for each listed name"
      pattern: "reader\\.read\\("
---

<objective>
Switch the Claude briefing result feed from one array file per task (`<taskId>.json`) to one dated file per run
(`<taskId>-<UTC timestamp>.json`, e.g. `jira_digest-2026-10-28T054412Z.json`), each holding one run object with the
existing row shape. Drop the old array format entirely (no data exists, no compat shim).

Purpose: Claude scheduled tasks can append a run by writing one new file instead of rewriting a shared array.
Output: updated adapter, rewritten unit tests, updated IT fixture, updated docs.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@./CLAUDE.md
@src/main/java/com/evision/releasemgmt/adapters/briefing/FileBriefingReadAdapter.java
@src/main/java/com/evision/releasemgmt/adapters/briefing/BriefingFiles.java
@src/test/java/com/evision/releasemgmt/adapters/briefing/FileBriefingReadAdapterTest.java
@briefings/README.md

<interfaces>
From BriefingFiles.java (same package, reuse unchanged):
```java
@FunctionalInterface interface Reader { Optional<byte[]> read(Path base, String fileName) throws IOException; }
// Empty when the directory or file does not exist; IOException for escape / symlink / non-regular / > MAX_BYTES (1 MiB).
static Optional<byte[]> readBounded(Path base, String fileName) throws IOException;
```
FileBriefingReadAdapter today: `feed(task)` reads `task.id() + ".json"`, requires an array, parses each element with
`private static Optional<BriefingRun> parseRow(JsonNode row)` (keep as is), sorts by `BriefingRun::runAt` reversed,
caps at `MAX_RUNS = 60`. `unreadable(task, exceptionType)` logs `task={} exceptionType={}` and returns readError true.
`BriefingFeed(BriefingTask task, List<BriefingRun> runs, boolean readError)`. Task ids: jira_digest, sprint_check,
weekly_status, reg_watch, gulf_invest (enum `BriefingTask`, `task.id()`).
`BriefingsProperties(Path dir, Path config, Duration initialDelay, Duration tickInterval, Duration catchUp, Headline headline)`
- no change needed. `.gitignore` already ignores `briefings/data/*` except `.gitkeep` - no change needed.
</interfaces>

Environment: every Maven call needs `export JAVA_HOME="$HOME/.jdks/jdk-25"`. Work on branch main. Do not touch licensing
code. Ignore everything under `.claude/worktrees/`. No Co-Authored-By trailers in commits. Never run `npm run dev`.
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Read one dated file per run in FileBriefingReadAdapter</name>
  <files>src/main/java/com/evision/releasemgmt/adapters/briefing/FileBriefingReadAdapter.java, src/test/java/com/evision/releasemgmt/adapters/briefing/FileBriefingReadAdapterTest.java</files>
  <behavior>
    Rewrite the fixtures in FileBriefingReadAdapterTest: add a helper `writeRun(String task, String stamp, String json)`
    writing `dir.resolve(task + "-" + stamp + ".json")`, where stamp is like `2026-10-28T054412Z`. Keep the `opened`
    recording reader, the log capture, `row(...)` helper, and the readBounded/cleanText/directoryReadable tests.
    - b1: 70 run files for jira_digest (stamps one minute apart, headline "run i") -> 60 runs, first "run 69", last "run 10", readError false.
    - b2: missing directory, and a directory with no jira_digest-*.json files (but containing `sprint_check-...json` and an
      old-format `jira_digest.json` array) -> jira_digest feed empty, readError false.
    - b3: jira_digest has one valid run file, one malformed (`{`), one non-object (`[...]` array) and one holding
      `"secret-content"` as a bad row -> only the valid run returned, readError false; at least one warn log; no log
      message contains the dir path, "secret-content" or file content; skipped-file logs contain the task id.
    - b4: one file over BriefingFiles.MAX_BYTES plus one valid file -> valid run returned, readError false.
    - b5: same cases as today's b5 but each row in its own file (non-object case = a file holding a JSON string); expect
      headlines exactly "offset date", "abcdef", "a"x140 and the same truncation/attention asserts.
    - b6: files `jira_digest-...json`, `sprint_check-...json`, `jira_digest-x.txt`, `jira_digest.json`, `other.json`,
      plus (if the OS allows) a symlink `sprint_check-2026-10-28T060000Z.json` -> other.json. Feed every task. Every name
      in `opened` starts with some `task.id() + "-"` and ends with ".json"; jira_digest never opened a `sprint_check-`
      name; `jira_digest.json`, `other.json` and the .txt were never opened. If symlinked: sprint_check feed still shows
      its regular run and readError false (symlink skipped). Keep the `Assumptions.assumeTrue(symlinked, ...)` at the end.
    - listing failure: props.dir() points at a regular file (exists, not a directory) -> readError true.
  </behavior>
  <action>
    Write the tests first (RED), then change `feed(task)`:
    1. `Path dir = props.dir();` If `Files.notExists(dir)` return `new BriefingFeed(task, List.of(), false)` (keeps
       today's "missing directory = not run yet" semantics).
    2. In a `try (Stream<Path> listing = Files.list(dir))` map to `getFileName().toString()`, filter
       `name.startsWith(task.id() + "-") && name.endsWith(".json")`, sort `Comparator.reverseOrder()` (the UTC stamp
       sorts lexicographically), `limit(MAX_RUNS)`, `toList()`. Catch `IOException | RuntimeException` around the
       listing only -> `return unreadable(task, e.getClass().getSimpleName())` (readError true). Use plain
       startsWith/endsWith, not a glob - task ids contain underscores and the `-` after the id is what stops one task
       picking up another's files. Names come only from this listing filtered by the enum-derived prefix.
    3. For each name: `reader.read(dir, name)` (keep the injectable `BriefingFiles.Reader` seam); empty Optional (file
       removed between list and read) -> skip. `mapper.readTree(bytes)`; `parseRow(node)` already rejects non-objects;
       add valid runs. Catch `IOException | RuntimeException` per file -> `log.warn("briefing run file skipped task={} exceptionType={}", task.id(), e.getClass().getSimpleName())`
       and continue. Never log the file name, path or content.
    4. Sort runs by `BriefingRun::runAt` reversed and return `new BriefingFeed(task, List.copyOf(runs), false)` (the
       listing already capped at MAX_RUNS).
    Remove the array handling and the "NotAnArray" path. Update the class comment to say file names come from the
    directory listing filtered by the enum-derived prefix, never from a request. Keep `directoryReadable()` and
    `parseRow`/`text`/`parseInstant` unchanged. Run `mvn clean` first only if compile errors suggest stale classes.
  </action>
  <verify>
    <automated>export JAVA_HOME="$HOME/.jdks/jdk-25" && cd /d/IdeaProjects/ReleaseManagement && mvn -q test -Dtest=FileBriefingReadAdapterTest</automated>
  </verify>
  <done>FileBriefingReadAdapterTest passes with all behaviours above; the old `<taskId>.json` array format is no longer read anywhere in the adapter.</done>
</task>

<task type="auto">
  <name>Task 2: Move the IT fixture and docs to the dated per-run format</name>
  <files>src/test/java/com/evision/releasemgmt/adapters/web/BriefingFlowIT.java, briefings/README.md, docs/briefings-flow.md, docs/docker.md</files>
  <action>
    BriefingFlowIT: add a constant like `private static final String CLAUDE_RUN_FILE = "jira_digest-2026-10-25T050000Z.json";`
    `writeClaudeDigest(status)` writes a single object (drop the surrounding `[` `]`) to `DATA_DIR.resolve(CLAUDE_RUN_FILE)`;
    `cleanUp()` deletes `DATA_DIR.resolve(CLAUDE_RUN_FILE)` instead of `jira_digest.json`. Keep CLAUDE_ROW_AT etc. as is.

    briefings/README.md: intro line -> Claude tasks write one result file per run. "Data contract": one file per run,
    `briefings/data/<id>-<UTC timestamp>.json` (e.g. `jira_digest-2026-10-28T054412Z.json`, stamp format
    `yyyy-MM-ddTHHmmssZ` so names sort by time), holding a single run object (example without the array brackets).
    The app reads the newest 60 files per task by file name and orders runs by `run_at`. No files = not run yet; a
    malformed file, one over 1 MB, or a symlink is skipped (logged) and the other runs still show; an unlistable
    directory shows the task as unreadable. Write atomically: write a temp name that does not end in `.json`
    (e.g. `.tmp`), then rename. Pruning is the writer's job - the app's mount is read-only and it never deletes;
    keep at least 60 recent files per task and delete older ones. In the config.json table change `output.keepRuns`
    wording from "per file" to "per task".

    docs/briefings-flow.md: replace every `briefings/data/<task>.json` with `briefings/data/<task>-<UTC timestamp>.json`
    (sections 1, 2 table row "one JSON array per task, oldest first" -> "one JSON object per run", 4, 10, and the
    "Claude's side of the contract" line: "one file per run, atomic rename, writer prunes"); rewrite "Claude's file
    format" (section 2) to the single-object example and the atomic/pruning rules; mermaid participants
    `briefings/data/jira_digest.json` -> `briefings/data/jira_digest-*.json`; section 9 "Only the five fixed file names
    are read" -> "Only files named `<task id>-*.json` for the five fixed task ids are read, from a directory listing,
    never a path from a request or the database"; "malformed rows are rejected" -> malformed files/rows are skipped.

    docs/docker.md "Briefings": add one bullet that the mount is read-only, so the writer must prune old
    `<task>-<timestamp>.json` files itself. No other change there.

    Do not edit `scheduledTasks/briefings-build-prompt.md` (historical build prompt) or `.planning/phases/17.1-*`.
    `.gitignore` (`briefings/data/*`) and `BriefingsProperties` already fit - leave them.
  </action>
  <verify>
    <automated>export JAVA_HOME="$HOME/.jdks/jdk-25" && cd /d/IdeaProjects/ReleaseManagement && mvn -q test -Dtest='FileBriefingReadAdapterTest,BriefingFlowIT,Briefing*Test'</automated>
  </verify>
  <done>BriefingFlowIT and all Briefing* tests pass (BriefingFlowIT needs Docker for Testcontainers; if Docker is unavailable, report that and confirm `mvn -q test-compile` succeeds); README, briefings-flow.md and docker.md describe only the dated per-run format and writer-side pruning.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Claude writer -> briefings/data (read-only mount) -> app | File names and contents are untrusted |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-jgg-01 | Tampering / Info disclosure | FileBriefingReadAdapter listing | mitigate | Names only from `Files.list(props.dir())` filtered by `task.id() + "-"` prefix and `.json` suffix; read via `BriefingFiles.readBounded`, which re-checks the path stays under the base dir and refuses symlinks/non-regular files |
| T-jgg-02 | Denial of service | FileBriefingReadAdapter.feed | mitigate | At most MAX_RUNS (60) files read per task, each capped at 1 MiB by readBounded |
| T-jgg-03 | Info disclosure | skip/unreadable logging | mitigate | Logs carry task id and exception type only - no file name, path or content (asserted in b3) |
| T-jgg-04 | Spoofing | one task's files read as another's | mitigate | `-` delimiter after the enum id; b6 asserts no cross-task opens |
</threat_model>

<verification>
- `mvn -q test -Dtest=FileBriefingReadAdapterTest` passes.
- `mvn -q test -Dtest='BriefingFlowIT,Briefing*Test'` passes (Docker required for the IT).
</verification>

<success_criteria>
- Claude results are read from `<taskId>-<UTC timestamp>.json` single-run files only; old array format gone.
- Bad individual files never hide good runs; unlistable directory = readError true; no files = not run yet.
- Docs state the new contract and that pruning is the writer's job.
</success_criteria>

<output>
After completion, create `.planning/quick/261028-jgg-briefings-read-one-dated-claude-result-f/261028-jgg-SUMMARY.md`
</output>
