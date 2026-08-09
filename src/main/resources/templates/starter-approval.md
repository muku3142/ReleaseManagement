# Change approval — {{release.name}} {{release.version}}

| Field | Value |
|---|---|
| Release | {{release.name}} |
| Version | {{release.version}}  |
| Status | {{release.status}} |
| Created | {{release.createdAt}} |
| Frozen | {{release.frozenAt}} |

## Scope of change

{{#items}}
- {{title}}{{^isManual}} — {{key}} ({{issueType}}){{/isManual}}{{#isManual}} — manual item ({{category}}){{/isManual}}
{{/items}}
{{^items}}
_Nothing is in scope for this release._
{{/items}}

## Risk

_Describe the risk this change carries._

## Impact

_Describe the systems and users affected._

## Backout plan

_Describe how this change is reversed._
