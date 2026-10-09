#! P. Leadership, HR, and career management — private personal records.

=== one-to-one-preparation
name: One-to-one preparation
category: Leadership, HR, and career management
icon: person
description: Prepare your topics before a 1:1.
## Prep
text with "With" *
list wins "Wins to share"
list blockers "Blockers"
list asks "Asks"
list feedback "Feedback to give or request"

=== feedback-preparation
name: Feedback preparation
category: Leadership, HR, and career management
icon: feedback
description: Prepare specific, kind and useful feedback (situation–behaviour–impact).
## Feedback
text for_whom "For" *
long situation "Situation"
long behaviour "Behaviour"
long impact "Impact"
text request "Request / suggestion"

=== performance-review
name: Performance review
category: Leadership, HR, and career management
icon: grading
description: Self-review or notes for a review: achievements, growth and goals.
## Review
text period "Period" *
list achievements "Achievements (with evidence)"
list growth "Growth areas"
list feedback "Feedback received"
list goals "Goals for next period"

=== development-plan
name: Development plan
category: Leadership, HR, and career management
icon: trending_up
description: Skills to grow, how, and by when.
aliases: Skill development
## Plan
table skills "Skills" [Skill|Current 1–5|Target 1–5|How|By]
list support "Support needed"

=== career-goals
name: Career goals
category: Leadership, HR, and career management
icon: flag
description: Where you want your career to go and the next steps.
aliases: Career plan
## Goals
long vision "Five-year vision"
list goals "Goals for this year"
list experiences "Experiences to seek"
list people "People who could help"

=== skills-matrix
name: Skills matrix
category: Leadership, HR, and career management
icon: grid_on
description: Map skills against proficiency levels.
## Matrix
text scope "Person / role" *
table matrix "Skills" [Skill|Level 1–5|Evidence]

=== interview-preparation
name: Interview preparation
category: Leadership, HR, and career management
icon: event_seat
description: Research, stories and questions for a job interview.
## Interview
text company "Company" *
text role "Role"
datetime when "When"
long research "Company research"
table stories "Stories (STAR)" [Question|Situation & task|Action|Result]
list questions "Questions to ask"

=== interview-debrief
name: Interview debrief
category: Leadership, HR, and career management
icon: assignment_turned_in
description: Reflect right after an interview.
## Debrief
text company "Company" *
long went_well "Went well"
long improve "Could improve"
list follow_up "Follow-ups"
rating feeling "How I feel about the role" max=5

=== role-research
name: Role research
category: Leadership, HR, and career management
icon: work
description: Understand a role before applying.
## Role
text role "Role" *
text company "Company"
url posting "Posting"
list requirements "Requirements"
list fit "My fit"
list gaps "Gaps"

=== job-application-tracker
name: Job application tracker
category: Leadership, HR, and career management
icon: work_history
description: Track applications and their stages.
## Applications
text search "Search" * ? "e.g. 2025 job search"
table apps "Applications" [Company|Role|Applied|Stage|Next step]

=== resume-achievements
name: Resume achievements
category: Leadership, HR, and career management
icon: workspace_premium
description: Achievements with measurable results for your CV.
## Achievement +repeat
text achievement "Achievement" *
text role "Role / period"
text result "Measurable result"

=== promotion-case
name: Promotion case
category: Leadership, HR, and career management
icon: north_east
description: Evidence that you're operating at the next level.
## Case
text target "Target level / role" *
table evidence "Evidence" [Expectation|Example|Impact]
list sponsors "Supporters"
list gaps "Gaps to close"

=== personal-leadership-principles
name: Personal leadership principles
category: Leadership, HR, and career management
icon: stars
description: Principles you lead by, with examples.
aliases: Personal principles
## Principles
table principles "Principles" [Principle|What it looks like|What it doesn't]
long origin "Where these come from"

=== delegation-plan
name: Delegation plan
category: Leadership, HR, and career management
icon: assignment_ind
description: What to delegate, to whom, with how much support (private planning).
## Plan
table tasks "Tasks" [Task|To|Support level|Check-in]
long rationale "Rationale"

=== coaching-notes
name: Coaching notes
category: Leadership, HR, and career management
icon: sports
description: Notes from a coaching conversation.
aliases: Mentoring session
## Session
text with "With" *
date day "Date" = today
text goal "Goal"
long reality "Current reality"
list options "Options"
text will "Will do"

=== training-plan
name: Training plan
category: Leadership, HR, and career management
icon: model_training
description: Courses and practice to build a capability.
## Plan
text capability "Capability" *
table items "Items" [Course / activity|When|Status]
text measure "How I'll measure progress"

=== onboarding-checklist
name: Onboarding checklist
category: Leadership, HR, and career management
icon: login
description: First weeks in a new role.
## Onboarding
text role "Role" *
checklist week1 "Week 1" = "Accounts and access|Meet manager|Meet key people|Read core docs"
checklist month1 "First month"
list questions "Questions"

=== offboarding-checklist
name: Offboarding checklist
category: Leadership, HR, and career management
icon: logout
description: Leaving a role cleanly.
## Offboarding
text role "Role" *
checklist steps "Steps" = "Handover notes|Return equipment|Transfer ownership|Personal files exported|Goodbyes"
date last_day "Last day"

=== hiring-scorecard
name: Hiring scorecard
category: Leadership, HR, and career management
icon: scoreboard
description: Your private assessment of a candidate against criteria.
private: true
## Scorecard
text candidate "Candidate" *
text role "Role"
table criteria "Criteria" [Criterion|Score 1–5|Evidence]
select recommendation "Recommendation" [Strong yes|Yes|No|Strong no] = Yes

=== professional-networking-notes
name: Professional networking notes
category: Leadership, HR, and career management
icon: diversity_1
description: People you've met and how to stay in touch.
aliases: Networking notes
## Contact +repeat
text person "Person" *
text where "Where we met"
text topics "Topics"
date follow_up "Follow up"
