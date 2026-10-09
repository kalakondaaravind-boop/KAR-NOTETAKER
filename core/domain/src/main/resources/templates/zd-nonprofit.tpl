#! AD. Nonprofit, volunteering, and community

=== fundraising-idea
name: Fundraising idea
category: Nonprofit, volunteering, and community
icon: volunteer_activism
description: An idea for raising funds with effort and expected return.
## Idea
text idea "Idea" *
text audience "Audience"
currency target "Target"
text effort "Effort required"
list steps "First steps"

=== grant-research
name: Grant research
category: Nonprofit, volunteering, and community
icon: search
description: Funders, fit and deadlines.
## Grants
table grants "Grants" [Funder|Amount|Fit|Deadline]
list notes "Notes"

=== grant-application-outline
name: Grant application outline
category: Nonprofit, volunteering, and community
icon: edit_document
description: The parts of a grant application.
## Application
text funder "Funder" *
date deadline "Deadline"
long need "Statement of need"
long project "Project description"
list outcomes "Outcomes"
currency amount "Amount requested"
long evaluation "Evaluation plan"

=== program-plan
name: Program plan
category: Nonprofit, volunteering, and community
icon: event_note
description: Goals, activities, resources and timeline for a program.
## Program
text program "Program" *
list goals "Goals"
list activities "Activities"
list resources "Resources"
table timeline "Timeline" [Phase|Dates]

=== impact-measurement
name: Impact measurement
category: Nonprofit, volunteering, and community
icon: insights
description: Inputs, outputs, outcomes and indicators.
## Impact
text program "Program" *
table logic "Logic model" [Inputs|Activities|Outputs|Outcomes|Indicators]

=== community-needs-assessment
name: Community needs assessment
category: Nonprofit, volunteering, and community
icon: diversity_3
description: Understand a community's needs and assets.
## Assessment
text community "Community" *
list needs "Needs"
list assets "Assets"
list sources "Sources consulted"
list priorities "Priorities"

=== outreach-plan
name: Outreach plan
category: Nonprofit, volunteering, and community
icon: campaign
description: Who to reach, how and with what message.
## Plan
text goal "Goal" *
table audiences "Audiences" [Audience|Channel|Message]
date start "Start"

=== volunteer-reflection
name: Volunteer reflection
category: Nonprofit, volunteering, and community
icon: self_improvement
description: Reflect on a volunteering experience.
## Reflection
text activity "Activity" *
long meaningful "What felt meaningful"
long challenging "What was challenging"
text next "Next time"

=== donation-record
name: Donation record
category: Nonprofit, volunteering, and community
icon: savings
description: Donations made, for your records and tax purposes.
private: true
## Donations
table donations "Donations" [Date|Organisation|Amount|Receipt?]
currency total "Total"

=== program-retrospective
name: Program retrospective
category: Nonprofit, volunteering, and community
icon: replay
description: Review a program's results and lessons.
## Retro
text program "Program" *
list achieved "Achieved"
list missed "Missed"
list lessons "Lessons"
