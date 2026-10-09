#! E. Project and program management

=== project-brief
name: Project brief
category: Project and program management
icon: description
description: A one-page summary of why, what and how for a project.
## Brief
text project "Project" *
long problem "Problem / opportunity"
list objectives "Objectives"
long scope "Scope"
list out_of_scope "Out of scope"
date start "Start"
date end "Target end"
list success "Success measures"

=== project-charter
name: Project charter
category: Project and program management
icon: verified
description: Formal authorisation of a project: purpose, scope, roles and constraints.
## Charter
text project "Project" *
text sponsor "Sponsor"
long purpose "Purpose and justification"
list objectives "Measurable objectives"
long scope "High-level scope"
list constraints "Constraints"
list assumptions "Assumptions"
table roles "Roles" [Role|Name|Responsibility]
currency budget "Budget"

=== project-plan
name: Project plan
category: Project and program management
icon: account_tree
description: Phases, milestones, resources and risks in one plan.
## Plan
text project "Project" *
## Phases +repeat
text phase "Phase" *
date start "Start"
date end "End"
list deliverables "Deliverables"
## Controls
list risks "Key risks"
text cadence "Status reporting cadence"

=== scope-definition
name: Scope definition
category: Project and program management
icon: crop
description: Define what's in and out, with acceptance criteria.
## Scope
text project "Project" *
list in_scope "In scope"
list out_scope "Out of scope"
list deliverables "Deliverables"
list acceptance "Acceptance criteria"
list exclusions "Explicit exclusions"

=== deliverables-register
name: Deliverables register
category: Project and program management
icon: inventory_2
description: Track every deliverable, owner, due date and acceptance.
## Deliverables
text project "Project" *
table deliverables "Deliverables" [Deliverable|Owner|Due|Status|Accepted?]

=== project-status-report
name: Project status report
category: Project and program management
icon: summarize
description: Status with RAG rating, progress, risks and asks.
title: {{field:project}} status — {{date}}
aliases: Status report
## Status
text project "Project" *
date period "Reporting date" = today
select rag "Overall status" [Green|Amber|Red] = Green
long why_rag "Why not green?" if=rag:Amber
long why_red "What's needed to recover?" if=rag:Red
progress progress "Progress"
## Details
list done "Done this period"
list next "Next period"
list risks "Risks and issues"
list asks "Decisions / help needed"

=== project-retrospective
name: Project retrospective
category: Project and program management
icon: replay_circle_filled
description: Review a completed project: outcomes versus plan and lessons.
## Retro
text project "Project" *
long outcome "Outcome vs objectives"
list well "What went well"
list improve "What to improve"
table metrics "Metrics" [Metric|Planned|Actual]
checklist actions "Follow-up actions"

=== lessons-learned
name: Lessons learned
category: Project and program management
icon: lightbulb_circle
description: Record lessons with context and recommendations for next time.
## Lessons
text source "Project / event" *
table lessons "Lessons" [Situation|What happened|Lesson|Recommendation]

=== dependency-register
name: Dependency register
category: Project and program management
icon: link
description: Track dependencies between work items and external parties.
## Dependencies
text project "Project" *
table deps "Dependencies" [Dependency|Depends on|Needed by|Status|Notes]

=== change-request
name: Change request
category: Project and program management
icon: change_circle
description: Document a proposed change and its impact on scope, time and cost.
## Change
text title_line "Change" *
date raised "Raised" = today
long description "Description"
long reason "Reason"
table impact "Impact" [Area|Impact]
select decision "Decision" [Pending|Approved|Rejected|Deferred] = Pending
text decided_by "Decided by"

=== assumptions-log
name: Assumptions log
category: Project and program management
icon: help_outline
description: Make assumptions explicit and track their validation.
aliases: Financial assumptions log
## Assumptions
text scope "Scope" *
table assumptions "Assumptions" [Assumption|Impact if wrong|How to validate|Status]

=== raid-log
name: RAID log
category: Project and program management
icon: table_chart
description: Risks, assumptions, issues and dependencies in one log.
## RAID
text project "Project" *
table risks "Risks" [Risk|Likelihood|Impact|Mitigation]
table assumptions "Assumptions" [Assumption|Validation]
table issues "Issues" [Issue|Priority|Resolution]
table dependencies "Dependencies" [Dependency|Status]

=== work-breakdown-structure
name: Work breakdown structure
category: Project and program management
icon: lan
description: Decompose a project into deliverables and work packages.
## WBS
text project "Project" *
## Deliverable +repeat
text deliverable "Deliverable" *
list packages "Work packages"
number effort "Estimated effort (days)" min=0

=== requirements-register
name: Requirements register
category: Project and program management
icon: rule
description: Track requirements with priority, source and status.
## Requirements
text scope "Product / project" *
table reqs "Requirements" [ID|Requirement|Priority|Source|Status]

=== acceptance-criteria
name: Acceptance criteria
category: Project and program management
icon: done_all
description: Testable conditions that define when something is done.
## Criteria
text item "Feature / deliverable" *
long context "Context"
checklist criteria "Criteria (Given / When / Then)"
list out_of_scope "Not covered"

=== project-closure
name: Project closure
category: Project and program management
icon: task
description: Close a project formally and capture handover.
## Closure
text project "Project" *
date closed "Closed" = today
checklist closure "Closure checklist" = "Deliverables accepted|Lessons captured|Documentation archived|Accounts closed|Thank-yous sent"
long outcomes "Final outcomes"
list handover "Handover items"

=== personal-project-dashboard
name: Personal project dashboard
category: Project and program management
icon: dashboard
description: One page to see all your personal projects at a glance.
aliases: Project summary
## Projects
table projects "Projects" [Project|Status|Next action|Due]
list blocked "Blocked"
list done_recently "Recently finished"
