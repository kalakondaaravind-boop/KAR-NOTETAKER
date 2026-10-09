#! AF. Generic and meta-templates

=== blank-note
name: Blank note
category: Generic and meta-templates
icon: note
description: An empty note with an optional title.
title: {{field:heading}}
## Note
text heading "Title"
rich body "Body"

=== structured-note
name: Structured note
category: Generic and meta-templates
icon: view_agenda
description: Summary, details and next steps — a general structure for any topic.
## Note
text topic "Topic" *
text summary "Summary"
long details "Details"
list next "Next steps"

=== research-note
name: Research note
category: Generic and meta-templates
icon: biotech
description: A general-purpose research note with source and findings.
## Note
text topic "Topic" *
url source "Source"
long findings "Findings"
list questions "Questions"

=== comparison
name: Comparison
category: Generic and meta-templates
icon: compare_arrows
description: Compare two or more things across criteria.
## Comparison
text subject "What's being compared" *
table grid "Comparison" [Criterion|Option A|Option B|Option C]
text winner "Conclusion"

=== timeline
name: Timeline
category: Generic and meta-templates
icon: timeline
description: Events in date order.
## Timeline
text subject "Subject" *
table events "Events" [Date|Event|Notes]

=== reusable-document
name: Reusable document
category: Generic and meta-templates
icon: content_copy
description: A document skeleton with sections you fill each time.
## Document
text doc_title "Title" *
long intro "Introduction"
long body "Body"
long conclusion "Conclusion"

=== custom-template-specification
name: Custom template specification
category: Generic and meta-templates
icon: design_services
description: Plan a new custom template before building it.
## Spec
text template_name "Template name" *
text purpose "Purpose"
table fields "Fields" [Label|Type|Required?|Options]
text title_pattern "Title pattern"
