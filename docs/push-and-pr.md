# Pushing `main` and opening a PR into `master`

The runbook for getting local work onto the Azure DevOps remote. Written 2026-08-12.

## Why this isn't just `git push`

Two things about this repository make a plain push fail:

1. **The remote has no `main` branch.** `origin` (`dev.azure.com/evisionorg/ReleaseManagement`) holds only `master`, and the local `main` branch has no upstream configured. There is nothing to fast-forward onto.
2. **`main` and `origin/master` have diverged** — 40 commits ahead, 8 behind as of 2026-08-12.

The divergence is smaller than the commit counts suggest. The only *content* on the remote side that is missing locally is deployment plumbing:

```
.gitattributes   | 1 +
compose.app.yaml | 2 +-
rsync.sh         | 4 ++++
```

The commit-count gap is inflated by `380230b` ("Merged PR 3317: fixing doc generation"), whose SHA is not in `main`'s history even though its file content already is. Merging `origin/master` is therefore expected to be a small, clean merge.

## The sequence

Run from the repository root.

### 1. Confirm the tree is clean and no agent is mid-commit

```bash
git status --short
git log --oneline -3
```

Do not proceed while a GSD executor is running — it commits as it goes, and merging underneath it produces a half-phase commit.

### 2. Fetch and inspect what the remote has that you don't

```bash
git fetch origin
git log --oneline HEAD..origin/master
git diff --stat HEAD...origin/master
```

The `...` (three-dot) form is the one that matters: it diffs the merge base against `origin/master`, i.e. shows exactly what the remote added. The two-dot `..` form in `git log` lists commits, which is why the two disagree here.

### 3. Merge `origin/master` into `main`

```bash
git merge origin/master -m "chore: merge origin/master (deployment plumbing) into main"
```

This is what lets `master` fast-forward when the PR completes. Without it, `main` is not a superset of `master`, and the only way to move `master` would be `--force`, which **must not** be used here — it would discard the three infra commits and violates the project's git rules.

Note the ordering: this step is *not* a prerequisite for step 4. Creating a new remote branch needs no ancestry relationship to `master`. It is a prerequisite for the PR completing cleanly. Since the merge rewrites the working tree, it must not run while a GSD executor is mid-phase — which is why the two steps were separated in practice (see the run log below).

### 4. Push `main` and set its upstream

```bash
git push -u origin main
```

`-u` sets the tracking branch so later pushes are a bare `git push`. This creates `refs/heads/main` on the remote; it does not touch `master`.

### 5. Open the pull request

There is **no `az` CLI installed on this machine**, so the PR cannot be created from the terminal as things stand. Two options:

**Option A — the web UI** (works today). Open:

```
https://dev.azure.com/evisionorg/ReleaseManagement/_git/ReleaseManagement/pullrequestcreate?sourceRef=main&targetRef=master
```

That URL pre-selects `main` → `master` on the create-PR form.

**Option B — install the Azure CLI** and do it from the terminal:

```bash
# one-time setup
winget install Microsoft.AzureCLI
az extension add --name azure-devops
az devops configure --defaults organization=https://dev.azure.com/evisionorg project=ReleaseManagement

# then, per PR
az repos pr create \
  --repository ReleaseManagement \
  --source-branch main \
  --target-branch master \
  --title "<title>" \
  --description "<description>"
```

`gh` is not applicable — this is Azure DevOps, not GitHub.

## Run log

**2026-08-12** — step 4 executed:

```
$ git push -u origin main
branch 'main' set up to track 'origin/main'.
To https://dev.azure.com/evisionorg/ReleaseManagement/_git/ReleaseManagement
 * [new branch]      main -> main
```

Step 3 (the `origin/master` merge) was deliberately **not** run at that point — a GSD executor for phase 05.3 was mid-flight and owned the working tree. It remains outstanding, and the PR should not complete until it has run.

Pre-push safety check, run first because the push publishes to the org remote:

```bash
git ls-files --error-unmatch .env     # not tracked (gitignored; only .env.example is committed)
git grep -nIE "(RM_CREDENTIAL_KEY|PAT|password|secret|token)\s*[:=]\s*['\"][A-Za-z0-9+/=_-]{16,}" \
  -- . ':!.planning' ':!*.md'         # no matches
```

## Notes

- **Never force-push `master`.** It carries other people's merged work; `380230b` is a merged PR.
- **The branch carries ~30 `.planning/` documentation commits** alongside the source changes. If reviewers should see only source, `/gsd-pr-branch` builds a filtered branch with the `.planning/` commits stripped, and you would push and PR *that* branch instead of `main`.
- The team's convention is PRs into `master` (see PR 3317), not direct pushes to it. Pushing `main` as its own branch keeps that convention intact.
