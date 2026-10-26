---
phase: 02-jira-ticket-browsing
type: spike
subject: jira-to-azure-devops-link-discovery
captured: 2026-10-07
ticket: CNV-2896
verdict: GO
---

# Jira to Azure DevOps link discovery (dev-status spike)

## 1. Verdict

**GO for discovery, with a commit-coverage limitation.** `/rest/dev-status/1.0/issue/detail` returns Azure DevOps branches, pull requests and commits for CNV-2896 on this tenant, so Phase 6 can find a ticket's ADO repository, branch and PRs from Jira alone, with no ADO search step. I call it GO rather than PARTIAL because discovery (the purpose of the spike, and the only thing the Jira call was ever meant to do) works completely. The limitation does not disqualify the mechanism, it narrows its role: dev-status lists only commits whose message references the issue key.

**Limitation, stated prominently:** CNV-2896's branch has a `lastCommit` ("adjust logs wording", no key in the message) that is absent from the repository commit list, which holds exactly one commit (the one that names the key). So dev-status cannot answer "how many commits to reach Done" (INSIGHT-03). Phase 6 must use the Azure DevOps API (Get Pull Request Commits, or branch commits) with an org-scoped PAT for complete commit lists. dev-status supplies the coordinates (org, project, repo, branch, PR ids); ADO supplies the complete commit set.

The key-parsing contingency (searching ADO commit/PR messages for work-item keys) is not needed for discovery. It is also what dev-status already does internally, so it would add nothing.

## 2. The working call sequence

1. `GET {site}/rest/api/3/issue/CNV-2896?fields=id` returns the numeric internal id (`48939`). In production the id can instead come from the ticket search already run in Phase 2, saving this call.
2. `GET {site}/rest/dev-status/1.0/issue/summary?issueId=48939` lists which application types carry data, under `summary.<dataType>.byInstanceType`. Optional but is the only way to discover the type key.
3. `GET {site}/rest/dev-status/1.0/issue/detail?issueId=48939&applicationType=AzureRepos&dataType=branch` returns branches and pull requests.
4. The same URL with `dataType=repository` returns repositories with their commits.

**Pitfall 5 confirmed:** `issueId=CNV-2896` is rejected with `{"errorMessages":["An invalid ID was provided: 'CNV-2896'"]}`. The numeric id is required.

**Trap: a wrong `applicationType` looks exactly like "no ADO link".** The working value is `AzureRepos`, which is the `byInstanceType` KEY in the summary response. The display name "Azure Repos", and RESEARCH.md's guesses `azure` and `AzureDevOpsGit`, all return HTTP 200 with `{"errors":[],"detail":[]}`, as does `cloud-providers`. An empty `detail` is therefore indistinguishable from an unlinked ticket. Phase 6 must read the type key from the summary (or pin `AzureRepos` in configuration and test it), never guess it. `JiraDevStatusSpikeAdapterTest.emptyDetailIsAnOrdinaryResultNotAnError` records that an empty detail is an ordinary non-throwing result.

## 3. Which `detail` branches populate

| dataType | Populated | Observed count | Key fields |
|----------|-----------|----------------|------------|
| `branch` | yes | 1 branch | `name`, `url`, `repository{id,name,url}`, `lastCommit{id,displayId,authorTimestamp,url,author{name,avatar},fileCount,merge,message,files[]}` |
| `pullrequest` | yes, byte-identical payload to `branch` | 3 PRs | `id`, `name` (title), `author{name,avatar}`, `commentCount`, `source{branch,url}`, `destination{branch}`, `reviewers[]`, `status` (OPEN/MERGED), `url`, `lastUpdate`, `repositoryId`, `repositoryName`, `repositoryUrl`, `ari` |
| `repository` | yes | 1 repository, 1 commit | `repositories[].{id,name,url,commits[]}`; commit has `id`, `displayId`, `authorTimestamp`, `url`, `author{name,avatar}`, `fileCount`, `merge`, `message`, `files[]` (always empty) |
| commit | no separate dataType | n/a | Commits arrive only inside `repository` (and as `branches[].lastCommit`) |

Every payload has the envelope `{"errors":[],"detail":[{... ,"_instance":{"name","baseUrl","type":"AzureRepos","id":"azure-devops-for-jira","typeName","singleInstance":true}}]}`. In the `branch`/`pullrequest` payload `repositories` is present but empty. `author.name` can be a placeholder (`"User "`) for some PRs, so it is not a reliable identity.

`summary` also reports `build` data from `cloud-providers` ("Other providers"). That is pipeline status, which PROJECT.md excludes, so it is out of scope.

## 4. Azure DevOps organisation and repository naming

Everything is a full URL; nothing is a bare slug. Repository URLs have the shape `https://dev.azure.com/{organisation}/{project}/_git/{repo}`, so the organisation, project and repository name are all separable from one URL. The repository also carries a GUID `id` (also repeated as `repositoryId` on PRs). Branch URLs append `?version=GB{branch}`; commit URLs are `{repoUrl}/commit/{sha}`; PR URLs are `{repoUrl}/pullrequest/{id}`. PR ids are numeric strings. Organisation and project are not given as separate fields, so Phase 6 must parse them from the URL. The org-scoped PAT credential model (ADO-04) keys on exactly that organisation segment.

## 5. Do commits and pull requests arrive in one call?

Branches and pull requests arrive together in **one call** (`dataType=branch` and `dataType=pullrequest` return identical payloads, so a second call is redundant). Commits need a **separate call** (`dataType=repository`). That is 2 dev-status calls per ticket after the id is known (3 if the id lookup is not folded into the search). Across a release of N tickets that is 2N Jira calls plus the ADO calls for complete commit lists, so ADO-05's "bounded time through parallel, cached lookups" is a real constraint, not a non-issue. The ADO commit fetch is the expensive part, not dev-status.

## 6. Schema-drift exposure

Phase 6 would depend on: `detail[0].branches[].name/url/repository`, `pullRequests[].id/name/status/source.branch/destination.branch/url/repositoryUrl`, `repositories[].commits[]`, and `_instance.type`. `JiraDevStatusSpikeAdapterTest` loads the redacted real capture and asserts the exact top-level keys of the envelope and element (`errors`, `detail`; `branches`, `pullRequests`, `repositories`, `_instance`), the element counts, `lastCommit.message`, `source.branch`, the commit fields under `repositories`, and `_instance.type`. A renamed or removed key fails the build. It does not detect a new `applicationType` key naming, which only a live call reveals.

## 7. What this means for Phase 6 scope

- Use dev-status for discovery only (org, project, repo, branch, PR ids); use the ADO REST API for commit lists. Do not plan INSIGHT-03 commit counts on dev-status data.
- Read `applicationType` from the summary `byInstanceType` keys. Treat an empty `detail` as "no ADO link" and degrade gracefully, but log which type was queried so a wrong key is diagnosable.
- Count commits only from PRs whose source branch is the ticket's own branch (names containing the key, e.g. `CNV-2896-...`). PR 3211 in the capture is `develop` to `testing`, an environment merge linked only because its title mentions the key; its commits include other tickets' work.
- Parse org and project from `repositoryUrl`; credentials are per org (ADO-04), so group tickets by org before fanning out.
- Budget 2 dev-status calls per ticket plus ADO calls; make them parallel, bounded and cached (ADO-05). The numeric id can come from the existing Jira search.
- The tenant has an issue link type `Problem/Incident` (inward "is caused by", outward "causes"); it is the candidate for INSIGHT-03's caused-by bugs. CNV-2896 has no issue links, so the shape of a populated `issuelinks` was not observed and needs a ticket that has them.
