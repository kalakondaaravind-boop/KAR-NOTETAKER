#! A. Everyday notes and personal organization

=== quick-note
name: Quick note
category: Everyday notes and personal organization
icon: bolt
description: A blank page with an optional context line — for capturing something fast.
use: When you just need to get it down.
title: Note — {{date}}
aliases: Information capture
## Note
long body "Note" ? "Type anything…"
text context "Context (optional)" ? "Where did this come from?"

=== daily-note
name: Daily notes
category: Everyday notes and personal organization
icon: today
description: One running note per day: plan, log and loose ends.
title: {{date}} — Daily note
tags: daily
aliases: Daily note
## Plan
checklist top3 "Top three for today" = "|"
## Log
> Jot things down as the day happens, with times if useful.
list log "Log"
## Loose ends
list carry "Carry over to tomorrow"
long notes "Other notes"

=== brain-dump
name: Brain dump
category: Everyday notes and personal organization
icon: cloud
description: Empty your head, then sort what came out.
title: Brain dump — {{date}}
## Dump
> Write everything on your mind without filtering. Sort afterwards.
long dump "Everything on my mind" *
## Sort
checklist actions "Actions to take"
list later "Someday / maybe"
list worries "Worries to let go of or schedule"

=== ideas-inbox
name: Ideas inbox
category: Everyday notes and personal organization
icon: lightbulb
description: A holding pen for ideas to review later.
title: Ideas inbox
aliases: Idea capture
## Idea +repeat
text idea "Idea" *
long why "Why it might matter"
select stage "Stage" [Raw|Worth exploring|Parked|Done] = Raw
date captured "Captured" = today

=== general-reference
name: General reference
category: Everyday notes and personal organization
icon: bookmark
description: A tidy page for reference information you look up repeatedly.
aliases: Reference entry, Things to remember
## Reference
text topic "Topic" *
long summary "Summary"
list key_facts "Key facts"
url source "Source link"
date verified "Last verified" = today

=== important-information
name: Important information
category: Everyday notes and personal organization
icon: priority_high
description: Critical details to keep in one place (policy numbers, contacts, instructions).
private: true
aliases: Frequently used information
## Details
text subject "Subject" *
table items "Items" [Item|Value|Notes]
long instructions "Instructions / what to do"
text where "Where originals are kept"
date reviewed "Last reviewed" = today

=== contact-reference
name: Contact reference
category: Everyday notes and personal organization
icon: person
description: Private notes about a contact — how you know them and what to remember.
private: true
## Person
text name "Name" *
text role "Role / organisation"
email email "Email"
text phone "Phone"
url link "Profile or website"
## Context
long how_met "How we know each other"
list remember "Things to remember (interests, family, preferences)"
date last_contact "Last contact"

=== personal-information-inventory
name: Personal information inventory
category: Everyday notes and personal organization
icon: inventory
description: Where your important accounts and documents live — locations, not secrets.
private: true
disclaimer: Do not store passwords here. Record where things are and how to recover them; use a password manager for credentials.
## Inventory
table accounts "Accounts and services" [Service|Purpose|Recovery method|Notes]
table documents "Documents" [Document|Location|Expiry]
long emergency "If something happens to me, start here"

=== checklist
name: Checklist
category: Everyday notes and personal organization
icon: checklist
description: A simple reusable checklist.
aliases: Errands
## Checklist
text purpose "Purpose"
checklist items "Items" *
date due "Due"

=== packing-list
name: Packing list
category: Everyday notes and personal organization
icon: luggage
description: Pack by category so nothing gets forgotten.
title: Packing — {{field:trip}}
## Trip
text trip "Trip" *
number nights "Nights" min=0
select climate "Climate" [Warm|Mild|Cold|Mixed]
## Pack
checklist essentials "Essentials" = "ID / passport|Wallet and cards|Phone and charger|Keys|Medication"
checklist clothes "Clothes"
checklist toiletries "Toiletries" = "Toothbrush|Toothpaste|Deodorant"
checklist tech "Tech" = "Chargers|Adapters|Headphones"
checklist other "Other"

=== shopping-list
name: Shopping list
category: Everyday notes and personal organization
icon: shopping_cart
description: Grouped by store section to shop faster.
title: Shopping — {{date}}
## List
text store "Store"
checklist produce "Produce"
checklist dairy "Dairy and fridge"
checklist pantry "Pantry"
checklist household "Household"
checklist other "Other"
currency budget "Budget"

=== decision-record
name: Decision record
category: Everyday notes and personal organization
icon: gavel
description: Capture a decision, its options and reasoning so you can revisit it later.
aliases: Financial decision record, Decision rationale, Meeting decision record
## Decision
text decision "Decision" *
date decided "Date" = today
select status "Status" [Proposed|Decided|Revisited|Reversed] = Decided
long context "Context — what prompted this?"
## Options considered
table options "Options" [Option|Pros|Cons]
## Reasoning
long rationale "Why this option"
list risks "Risks and what would change my mind"
date review_on "Review on"

=== pros-and-cons
name: Pros and cons
category: Everyday notes and personal organization
icon: balance
description: Weigh a choice with weighted pros and cons.
## Question
text question "What am I deciding?" *
## Weigh it
table pros "Pros" [Pro|Weight 1–5]
table cons "Cons" [Con|Weight 1–5]
long gut "Gut feeling"
select leaning "Leaning" [Yes|No|Undecided] = Undecided

=== questions-to-investigate
name: Questions to investigate
category: Everyday notes and personal organization
icon: help
description: Track open questions and what you found.
aliases: Question list, Open questions, Open question
## Question +repeat
text question "Question" *
select status "Status" [Open|Investigating|Answered|Dropped] = Open
long answer "What I found"
url source "Source"

=== information-to-verify
name: Information to verify
category: Everyday notes and personal organization
icon: fact_check
description: Claims you've heard that need checking before you rely on them.
## Claim +repeat
text claim "Claim" *
text heard_from "Heard from / where"
select status "Status" [Unverified|Confirmed|False|Unclear] = Unverified
url evidence "Evidence link"
long notes "Notes"

=== follow-up-list
name: Follow-up list
category: Everyday notes and personal organization
icon: reply
description: Things you owe people and things people owe you.
aliases: Follow-up reminders
## I owe
table i_owe "Things I owe" [Who|What|By when]
## Owed to me
table owed "Things owed to me" [Who|What|Chased on]

=== weekly-personal-review
name: Weekly personal review
category: Everyday notes and personal organization
icon: event_repeat
description: A calm weekly reset: clear, review, plan.
title: Weekly review — {{year}} {{week}}
tags: review
aliases: Weekly review, Weekly reflection
## Clear
checklist clear "Get clear" = "Empty inboxes|Process loose notes|Review calendar (past week)|Review calendar (next two weeks)"
## Reflect
list wins "Wins"
list lessons "Lessons"
rating week_rating "How was the week?" max=5
## Plan
checklist next_week "Priorities for next week"
long notes "Notes"

=== monthly-personal-review
name: Monthly personal review
category: Everyday notes and personal organization
icon: calendar_month
description: Look back at the month and set intentions for the next.
title: Monthly review — {{month}}
tags: review
aliases: Monthly reflection, Personal review
## Look back
list highlights "Highlights"
list difficulties "What was hard"
long learned "What I learned"
## Areas
table areas "Life areas" [Area|Score 1–10|Note]
## Look ahead
checklist intentions "Intentions for next month"

=== yearly-review
name: Yearly review
category: Everyday notes and personal organization
icon: event_note
description: A structured end-of-year reflection and plan.
title: Year in review — {{year}}
tags: review
aliases: Annual reflection
## The year
long summary "The year in a paragraph"
list proud "What I'm proud of"
list regrets "What I'd do differently"
list memorable "Most memorable moments"
## Next year
text theme "Theme / word for next year"
list goals "Goals"
list stop "What I'll stop doing"

=== personal-goals
name: Personal goals
category: Everyday notes and personal organization
icon: flag
description: Goals by life area with a reason and a first step.
aliases: Life goals
## Goal +repeat
text goal "Goal" *
select area "Area" [Health|Relationships|Career|Money|Learning|Fun|Home|Other]
long why "Why it matters"
date target "Target date"
text first_step "First step"
progress progress "Progress" = 0

=== goal-breakdown
name: Goal breakdown
category: Everyday notes and personal organization
icon: account_tree
description: Break a big goal into milestones and next actions.
aliases: Goal-setting worksheet
## Goal
text goal "Goal" *
long success "What does done look like?"
date deadline "Deadline"
## Milestones +repeat
text milestone "Milestone" *
date due "Due"
checklist steps "Steps"
## Obstacles
table obstacles "Obstacles" [Obstacle|Plan]

=== habit-tracker
name: Habit tracker
category: Everyday notes and personal organization
icon: repeat
description: Track a few habits across a week.
title: Habits — {{year}} {{week}}
aliases: Habit tracking
## Habits
table grid "This week" [Habit|Mon|Tue|Wed|Thu|Fri|Sat|Sun]
long reflection "What helped or got in the way?"

=== personal-milestone
name: Personal milestone
category: Everyday notes and personal organization
icon: emoji_events
description: Record a meaningful milestone and what led to it.
aliases: Personal milestones
## Milestone
text milestone "Milestone" *
date when "Date" = today
long story "What happened"
list who_helped "Who helped"
long meaning "What it means to me"

=== personal-operating-manual
name: Personal operating manual
category: Everyday notes and personal organization
icon: menu_book
description: How you work best — a private user guide to yourself.
## How I work
long best_conditions "Conditions where I do my best work"
long energy "What gives and drains energy"
long communication "How I prefer to communicate"
list values "Values I operate by"
list warning_signs "Signs I'm overloaded"
list resets "What helps me reset"
