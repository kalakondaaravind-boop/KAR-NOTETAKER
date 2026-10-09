#! M. Software engineering and IT

=== technical-design
name: Technical design
category: Software engineering and IT
icon: architecture
description: Problem, proposed design, alternatives and rollout.
aliases: System diagram notes
## Design
text feature "Feature / system" *
long context "Context and goals"
list non_goals "Non-goals"
long design "Proposed design"
list alternatives "Alternatives considered"
list risks "Risks"
long rollout "Rollout and testing"

=== architecture-decision-record
name: Architecture decision record
category: Software engineering and IT
icon: rule_folder
description: An ADR: context, decision, status and consequences.
title: ADR — {{field:decision}}
## ADR
text decision "Decision" *
select status "Status" [Proposed|Accepted|Deprecated|Superseded] = Proposed
date day "Date" = today
long context "Context"
long decision_text "Decision"
long consequences "Consequences"

=== api-reference
name: API reference
category: Software engineering and IT
icon: api
description: Reference notes for an API you use.
## API
text api "API" *
url docs "Docs"
text auth "Authentication"
text base_url "Base URL"
table endpoints "Endpoints" [Method|Path|Purpose]
list gotchas "Gotchas"

=== api-endpoint-notes
name: API endpoint notes
category: Software engineering and IT
icon: http
description: Details of one endpoint: request, response and errors.
## Endpoint
select method "Method" [GET|POST|PUT|PATCH|DELETE] = GET
text path "Path" *
long request "Request example"
long response "Response example"
table errors "Errors" [Code|Meaning]

=== bug-report
name: Bug report
category: Software engineering and IT
icon: bug_report
description: Steps, expected versus actual, environment.
## Bug
text summary "Summary" *
select severity "Severity" [Critical|Major|Minor|Trivial] = Major
list steps "Steps to reproduce"
long expected "Expected"
long actual "Actual"
text environment "Environment / version"

=== incident-report
name: Incident report
category: Software engineering and IT
icon: crisis_alert
description: What happened, impact, response and follow-ups.
aliases: Incident record
## Incident
text incident "Incident" *
select severity "Severity" [SEV1|SEV2|SEV3|SEV4] = SEV3
datetime started "Started"
datetime resolved "Resolved"
long impact "Impact"
long response "Response"
checklist follow_ups "Follow-ups"

=== root-cause-analysis
name: Root cause analysis
category: Software engineering and IT
icon: troubleshoot
description: Five whys plus contributing factors and corrective actions.
## RCA
text problem "Problem" *
list whys "Five whys"
text root_cause "Root cause"
list factors "Contributing factors"
checklist actions "Corrective actions"

=== postmortem
name: Postmortem
category: Software engineering and IT
icon: history_toggle_off
description: A blameless postmortem with timeline and lessons.
## Postmortem
text incident "Incident" *
long summary "Summary"
table timeline "Timeline" [Time|Event]
long root_cause "Root cause"
list went_well "What went well"
list went_wrong "What went wrong"
checklist actions "Action items"

=== runbook
name: Runbook
category: Software engineering and IT
icon: menu_book
description: Step-by-step operational procedure with checks.
aliases: Recovery procedure
## Runbook
text procedure "Procedure" *
text when "When to use"
list prerequisites "Prerequisites"
list steps "Steps"
list verify "Verification"
long rollback "Rollback"

=== deployment-checklist
name: Deployment checklist
category: Software engineering and IT
icon: rocket_launch
description: Pre-flight, deploy and post-deploy checks.
## Deploy
text release "Release" *
checklist before "Before" = "Tests green|Changelog updated|Migrations reviewed|Rollback plan ready"
checklist during "During" = "Deploy started|Health checks pass"
checklist after "After" = "Smoke test|Monitor dashboards|Announce"

=== release-notes
name: Release notes
category: Software engineering and IT
icon: new_releases
description: What changed in a release, for users.
## Release
text version "Version" *
date day "Date" = today
list new "New"
list improved "Improved"
list fixed "Fixed"
list known "Known issues"

=== code-review-notes
name: Code review notes for personal reference
category: Software engineering and IT
icon: rate_review
description: Your own notes on a code review — kept privately.
aliases: Code review notes
## Review
text change "Change / PR" *
list concerns "Concerns"
list praise "Good patterns"
list learn "Things I learned"

=== database-design
name: Database design
category: Software engineering and IT
icon: storage
description: Entities, relationships, indexes and constraints.
## Schema
text system "System" *
table entities "Entities" [Entity|Key fields|Relationships]
list indexes "Indexes"
list constraints "Constraints"
long migration "Migration strategy"

=== data-migration-plan
name: Data migration plan
category: Software engineering and IT
icon: move_down
description: Plan, validate and roll back a data migration.
## Migration
text migration "Migration" *
long source "Source"
long target "Target"
list steps "Steps"
list validation "Validation checks"
long rollback "Rollback plan"

=== performance-investigation
name: Performance investigation
category: Software engineering and IT
icon: speed
description: Measure, hypothesise, change, measure again.
## Investigation
text symptom "Symptom" *
text baseline "Baseline measurement"
table experiments "Experiments" [Hypothesis|Change|Result]
text conclusion "Conclusion"

=== security-review
name: Security review
category: Software engineering and IT
icon: security
description: Review a system for common security issues.
aliases: Privacy review
## Review
text system "System" *
checklist checks "Checks" = "Authentication|Authorisation|Input validation|Secrets handling|Logging of sensitive data|Dependencies up to date|Transport encryption"
list findings "Findings"
list actions "Actions"

=== threat-model
name: Threat model
category: Software engineering and IT
icon: shield
description: Assets, threats (STRIDE) and mitigations.
## Model
text system "System" *
list assets "Assets"
list boundaries "Trust boundaries"
table threats "Threats" [Threat|STRIDE category|Mitigation|Status]

=== test-plan
name: Test plan
category: Software engineering and IT
icon: checklist_rtl
description: Scope, approach and exit criteria for testing.
## Plan
text feature "Feature / release" *
list in_scope "In scope"
list approach "Approach"
list environments "Environments"
list exit "Exit criteria"

=== test-case
name: Test case
category: Software engineering and IT
icon: playlist_add_check
description: A single test with steps and expected result.
## Test
text test "Test" *
list preconditions "Preconditions"
list steps "Steps"
long expected "Expected result"
select result "Result" [Not run|Pass|Fail|Blocked] = "Not run"

=== troubleshooting-guide
name: Troubleshooting guide
category: Software engineering and IT
icon: build
description: Symptoms mapped to causes and fixes.
## Guide
text system "System" *
table issues "Issues" [Symptom|Likely cause|Fix]

=== environment-setup
name: Environment setup
category: Software engineering and IT
icon: terminal
description: How to set up a development environment from scratch.
## Setup
text project "Project" *
list requirements "Requirements"
list steps "Steps"
list verify "How to verify"
list issues "Common issues"

=== development-log
name: Development log
category: Software engineering and IT
icon: code
description: A daily engineering log of what you did and learned.
title: Dev log — {{date}}
## Log
list did "What I did"
list blocked "Blocked on"
list learned "Learned"
list tomorrow "Tomorrow"

=== technical-debt-register
name: Technical debt register
category: Software engineering and IT
icon: construction
description: Known shortcuts, their cost and when to pay them down.
## Debt
text system "System" *
table items "Items" [Item|Impact|Effort|Priority]

=== dependency-review
name: Dependency review
category: Software engineering and IT
icon: inventory_2
description: Review third-party dependencies for health, licence and risk.
aliases: Software dependency audit
## Review
text project "Project" *
table deps "Dependencies" [Package|Version|Licence|Last release|Risk]
list actions "Actions"

=== infrastructure-notes
name: Infrastructure notes
category: Software engineering and IT
icon: dns
description: Where things run and how they're configured.
## Infrastructure
text system "System" *
table components "Components" [Component|Where|Notes]
list access "How to access (no secrets)"
list monitoring "Monitoring"

=== devops-checklist
name: DevOps checklist
category: Software engineering and IT
icon: all_inclusive
description: Baseline operational practices for a service.
## Checklist
text service "Service" *
checklist practices "Practices" = "CI on every change|Automated deploys|Monitoring and alerts|Backups tested|Runbooks written|On-call defined"

=== disaster-recovery-plan
name: Disaster recovery plan
category: Software engineering and IT
icon: restore
description: RTO/RPO targets and recovery steps.
aliases: Continuity plan
## Plan
text system "System" *
text rto "Recovery time objective"
text rpo "Recovery point objective"
list scenarios "Scenarios"
list steps "Recovery steps"
date tested "Last tested"

=== slo-notes
name: Service-level objective notes
category: Software engineering and IT
icon: monitor_heart
description: Define SLIs, SLOs and error budgets.
## SLO
text service "Service" *
table slos "Objectives" [SLI|Target|Window]
text error_budget "Error budget policy"

=== feature-specification
name: Feature specification
category: Software engineering and IT
icon: spec
description: What a feature does, for whom, and how we'll know it works.
aliases: Component specification
## Spec
text feature "Feature" *
long problem "Problem"
list user_stories "User stories"
list requirements "Requirements"
list acceptance "Acceptance criteria"
list open_questions "Open questions"

=== user-story
name: User story
category: Software engineering and IT
icon: person_pin
description: As a…, I want…, so that… with acceptance criteria.
## Story
text as_a "As a" *
text i_want "I want"
text so_that "So that"
checklist criteria "Acceptance criteria"
select size "Size" [XS|S|M|L|XL] = M

=== developer-learning-notes
name: Developer learning notes
category: Software engineering and IT
icon: school
description: Notes while learning a language, library or tool.
## Learning
text topic "Topic" *
long concepts "Key concepts"
long snippet "Code snippet"
list gotchas "Gotchas"
list links "Links"
