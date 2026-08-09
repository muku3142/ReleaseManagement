# {{release.name}} — {{release.version}}

{{^items}}
_This release has no items yet._
{{/items}}

{{#groups}}
## {{groupName}}

{{#items}}
- **{{title}}**{{^isManual}} ({{key}}){{/isManual}}{{#isManual}} _(manual — {{category}})_{{/isManual}}
{{/items}}

{{/groups}}
