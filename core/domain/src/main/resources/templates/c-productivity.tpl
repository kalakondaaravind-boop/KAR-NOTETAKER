#! C. Productivity and planning

=== daily-planner
name: Daily planner
category: Productivity and planning
icon: today
description: Plan the day around priorities, appointments and time blocks.
title: Plan — {{date}}
tags: planning
## Day
date day "Date" = today
checklist must "Must do (max 3)"
checklist should "Should do"
table schedule "Schedule" [Time|What]
long notes "Notes"
## End of day
text best "Best part of the day"

=== weekly-planner
name: Weekly planner
category: Productivity and planning
icon: view_week
description: Set weekly outcomes and spread work across the days.
title: Week {{week}} — {{year}}
tags: planning
## Week
list outcomes "Outcomes for the week"
table days "By day" [Day|Focus|Key tasks]
list appointments "Appointments"
list waiting "Waiting on"

=== monthly-planner
name: Monthly planner
category: Productivity and planning
icon: calendar_month
description: Goals, key dates and focus areas for the month.
title: Plan — {{month}}
tags: planning
aliases: Monthly planning
## Month
list goals "Goals"
table dates "Key dates" [Date|Event]
list projects "Projects in focus"
list habits "Habits to build"
currency budget "Budget"

=== quarterly-planner
name: Quarterly planner
category: Productivity and planning
icon: date_range
description: Three-month objectives with monthly milestones.
## Quarter
text quarter "Quarter" * ? "e.g. Q3 2025"
list objectives "Objectives"
table milestones "Milestones" [Month|Milestone|Done?]
list risks "Risks"
long review "End-of-quarter review"

=== annual-planner
name: Annual planner
category: Productivity and planning
icon: event_note
description: Themes, goals and quarter-by-quarter plan for the year.
title: Plan — {{year}}
## Year
text theme "Theme"
table goals "Goals by area" [Area|Goal|Measure]
table quarters "Quarter plan" [Quarter|Focus|Milestones]
list not_doing "Not doing this year"

=== prioritization-matrix
name: Prioritization matrix
category: Productivity and planning
icon: grid_view
description: Score options by impact and effort to choose what to do first.
aliases: Feature prioritization
## Items
table items "Options" [Item|Impact 1–5|Effort 1–5|Confidence 1–5|Notes]
long decision "What I'll do first and why"

=== eisenhower-matrix
name: Eisenhower matrix
category: Productivity and planning
icon: apps
description: Sort tasks by urgency and importance.
## Matrix
checklist do_now "Urgent and important — do now"
checklist schedule "Important, not urgent — schedule"
checklist delegate "Urgent, not important — delegate or minimise"
list drop "Neither — drop"

=== personal-task-list
name: Personal task list
category: Productivity and planning
icon: task_alt
description: A simple personal task list with contexts.
## Tasks
checklist today "Today"
checklist this_week "This week"
checklist errands "Errands"
checklist someday "Someday"

=== project-task-list
name: Project task list
category: Productivity and planning
icon: assignment
description: Tasks for one personal project with owners and dates for your own tracking.
## Project
text project "Project" *
date deadline "Deadline"
## Tasks
table tasks "Tasks" [Task|Due|Status|Notes]
checklist next "Next actions"

=== milestone-tracker
name: Milestone tracker
category: Productivity and planning
icon: flag
description: Track milestones, dates and status.
aliases: Milestone plan
## Milestones
text project "Project" *
table milestones "Milestones" [Milestone|Target date|Status|Evidence]
long notes "Notes"

=== smart-goal
name: SMART goal
category: Productivity and planning
icon: track_changes
description: Make a goal specific, measurable, achievable, relevant and time-bound.
## SMART
text goal "Goal" *
long specific "Specific — what exactly?"
text measurable "Measurable — how will I measure it?"
long achievable "Achievable — what makes it realistic?"
long relevant "Relevant — why does it matter?"
date time_bound "Time-bound — by when?"

=== okr-planning
name: OKR planning
category: Productivity and planning
icon: insights
description: Objectives and measurable key results for a period.
## Period
text period "Period" * ? "e.g. H2 2025"
## Objective +repeat
text objective "Objective" *
table key_results "Key results" [Key result|Baseline|Target|Current]
list initiatives "Initiatives"

=== habit-review
name: Habit review
category: Productivity and planning
icon: repeat_on
description: Review which habits are sticking and adjust the system.
## Review
table habits "Habits" [Habit|Consistency %|What helps|What blocks]
list keep "Keep"
list change "Change"
list drop "Drop"

=== time-blocking-plan
name: Time-blocking plan
category: Productivity and planning
icon: schedule
description: Assign every hour of the day a job, with buffers.
title: Time blocks — {{date}}
## Blocks
date day "Date" = today
table blocks "Blocks" [Start|End|Block|Done?]
text buffer "Buffer / flex time"
long adjustments "Adjustments during the day"

=== focus-session
name: Focus session
category: Productivity and planning
icon: center_focus_strong
description: Plan and review a single deep-work session.
title: Focus — {{date}} {{time}}
## Before
text goal "Session goal" *
number minutes "Planned minutes" = 50 min=5 max=480
list distractions "Distractions to block"
## After
rating focus "Focus quality" max=5
long result "What I got done"
text next "Next step"

=== next-actions-list
name: Next-actions list
category: Productivity and planning
icon: arrow_forward
description: GTD-style next actions grouped by context.
## Next actions
checklist computer "At computer"
checklist calls "Calls"
checklist home "At home"
checklist out "Out and about"
checklist anywhere "Anywhere"

=== waiting-for-list
name: Waiting-for list
category: Productivity and planning
icon: hourglass_top
description: Things you're waiting on from others, with chase dates.
## Waiting for
table items "Waiting for" [Who|What|Since|Chase on]
date reviewed "Last reviewed" = today

=== delegated-task-record
name: Delegated-task record for personal reference
category: Productivity and planning
icon: forward_to_inbox
description: Your own private record of work you handed off — not a shared assignment system.
aliases: Delegated-task record
## Delegated +repeat
text task "Task" *
text to_whom "Handed to"
date handed "Handed off" = today
date due "Due"
select status "Status" [Pending|In progress|Done|Overdue] = Pending
long notes "Notes"

=== recurring-maintenance-checklist
name: Recurring maintenance checklist
category: Productivity and planning
icon: build
description: Routine maintenance tasks by frequency.
## Maintenance
checklist weekly "Weekly"
checklist monthly "Monthly"
checklist quarterly "Quarterly"
checklist yearly "Yearly"
date last_done "Last completed"

=== decision-log
name: Decision log
category: Productivity and planning
icon: list_alt
description: A running log of decisions with dates and reasons.
## Decisions
text scope "Area / project" *
table log "Decisions" [Date|Decision|Reason|Revisit?]

=== risk-register
name: Risk register
category: Productivity and planning
icon: warning
description: Track risks with likelihood, impact, owner and mitigation.
aliases: Operational risk register
## Risks
text scope "Scope" ? "Project or area"
table risks "Risks" [Risk|Likelihood 1–5|Impact 1–5|Mitigation|Owner|Status]
date reviewed "Last reviewed" = today

=== issue-log
name: Issue log
category: Productivity and planning
icon: report
description: Track issues from discovery to resolution.
aliases: Issue register
## Issues
text scope "Scope"
table issues "Issues" [Issue|Raised|Priority|Status|Resolution]
