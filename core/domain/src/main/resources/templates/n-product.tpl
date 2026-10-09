#! N. Product, UX, and design

=== product-brief
name: Product brief
category: Product, UX, and design
icon: inventory
description: The why, who and what of a product or initiative.
## Brief
text product "Product" *
long problem "Problem"
text target_users "Target users"
long solution "Proposed solution"
list metrics "Success metrics"
list risks "Risks"

=== product-requirements-document
name: Product requirements document
category: Product, UX, and design
icon: description
description: A PRD with goals, requirements and release criteria.
## PRD
text feature "Feature" *
long background "Background"
list goals "Goals"
list non_goals "Non-goals"
table requirements "Requirements" [Requirement|Priority|Notes]
list release "Release criteria"

=== user-persona
name: User persona
category: Product, UX, and design
icon: person
description: A research-based persona with goals and frustrations.
aliases: Customer persona
## Persona
text persona "Persona name" *
text role "Role / context"
list goals "Goals"
list frustrations "Frustrations"
list behaviours "Behaviours"
text quote "Representative quote"

=== user-journey
name: User journey
category: Product, UX, and design
icon: route
description: Stages, actions, feelings and opportunities along a journey.
aliases: Customer journey
## Journey
text persona "Persona" *
text scenario "Scenario"
table stages "Stages" [Stage|Actions|Thoughts / feelings|Pain points|Opportunities]

=== user-story-map
name: User story map
category: Product, UX, and design
icon: map
description: Activities and stories organised into release slices.
## Map
text product "Product" *
## Activity +repeat
text activity "Activity" *
list stories "Stories"
text release "Release slice"

=== jobs-to-be-done
name: Jobs-to-be-done
category: Product, UX, and design
icon: work_outline
description: The job a customer hires a product for, with forces of progress.
## Job
text job "When… I want to… so I can…" *
list push "Push of the situation"
list pull "Pull of the new solution"
list anxieties "Anxieties"
list habits "Habits of the present"

=== usability-test-plan
name: Usability test plan
category: Product, UX, and design
icon: touch_app
description: Goals, participants, tasks and metrics for a usability test.
## Plan
text product "Product" *
list goals "Goals"
text participants "Participants"
table tasks "Tasks" [Task|Success criteria]
list metrics "Metrics"

=== ux-audit
name: UX audit
category: Product, UX, and design
icon: plagiarism
description: Review a product's experience against goals and patterns.
## Audit
text product "Product" *
table findings "Findings" [Screen / flow|Issue|Severity|Recommendation]
list quick_wins "Quick wins"

=== heuristic-evaluation
name: Heuristic evaluation
category: Product, UX, and design
icon: checklist
description: Evaluate against usability heuristics.
## Evaluation
text product "Product" *
table heuristics "Heuristics" [Heuristic|Issues|Severity 0–4] = "Visibility of system status;;|Match with the real world;;|User control and freedom;;|Consistency and standards;;|Error prevention;;|Recognition rather than recall;;|Flexibility and efficiency;;|Aesthetic and minimalist design;;|Help users recover from errors;;|Help and documentation;;"

=== accessibility-audit
name: Accessibility audit
category: Product, UX, and design
icon: accessibility_new
description: Check a product against key accessibility criteria.
## Audit
text product "Product" *
checklist checks "Checks" = "Text alternatives|Colour contrast|Keyboard / switch access|Screen reader labels|Focus order|Text scaling|Captions|Error messages announced"
table issues "Issues" [Issue|Where|Severity|Fix]

=== competitive-product-review
name: Competitive product review
category: Product, UX, and design
icon: compare
description: Hands-on review of a competing product.
## Review
text product "Product" *
list strengths "Strengths"
list weaknesses "Weaknesses"
list ideas "Ideas to borrow"
rating overall "Overall" max=5

=== design-system-notes
name: Design system notes
category: Product, UX, and design
icon: widgets
description: Tokens, components and usage rules.
## System
text system "Design system" *
table tokens "Tokens" [Token|Value|Usage]
table components "Components" [Component|Status|Notes]
list principles "Principles"

=== wireframe-annotations
name: Wireframe annotations
category: Product, UX, and design
icon: draw
description: Annotate a screen's elements and behaviour.
## Screen
text screen "Screen" *
table annotations "Annotations" [Element|Behaviour|Notes]
list states "States (empty, loading, error)"

=== product-analytics-plan
name: Product analytics plan
category: Product, UX, and design
icon: analytics
description: Events and metrics needed to answer product questions.
aliases: Measurement plan
## Plan
text feature "Feature" *
list questions "Questions to answer"
table events "Events" [Event|Properties|Trigger]
list metrics "Metrics"

=== roadmap-proposal
name: Roadmap proposal
category: Product, UX, and design
icon: map
description: Now, next, later with rationale.
## Roadmap
text product "Product" *
list now "Now"
list next "Next"
list later "Later"
long rationale "Rationale"

=== launch-checklist
name: Launch checklist
category: Product, UX, and design
icon: rocket
description: Everything to check before and after a launch.
## Launch
text launch "Launch" *
date day "Launch date"
checklist before "Before" = "Positioning final|Docs and help ready|Support briefed|Analytics in place|Rollback plan"
checklist after "After" = "Monitor metrics|Collect feedback|Retrospective"

=== product-retrospective
name: Product retrospective
category: Product, UX, and design
icon: replay
description: Did the product change achieve its goals?
## Retro
text release "Release / feature" *
table metrics "Metrics" [Metric|Expected|Actual]
list learned "Learned"
list next "Next steps"

=== voice-of-customer-notes
name: Voice-of-customer notes
category: Product, UX, and design
icon: campaign
description: Themes from customer feedback with representative quotes.
aliases: Customer feedback
## Feedback
text source "Source / period" *
table themes "Themes" [Theme|Frequency|Representative quote]
list actions "Actions"
