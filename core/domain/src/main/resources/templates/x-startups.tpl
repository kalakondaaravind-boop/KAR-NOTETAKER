#! X. Startups and entrepreneurship

=== startup-idea
name: Startup idea
category: Startups and entrepreneurship
icon: rocket
description: A startup idea with problem, customer and why now.
## Idea
text idea "Idea" *
long problem "Problem"
text customer "Customer"
long why_now "Why now"
text unfair "Unfair advantage"
list risks "Biggest risks"

=== problem-validation
name: Problem validation
category: Startups and entrepreneurship
icon: fact_check
description: Evidence that a problem is real and painful.
## Validation
text problem "Problem" *
number interviews "Interviews done" min=0
list evidence "Evidence"
rating severity "Pain severity" max=5
select verdict "Verdict" [Validated|Not validated|Unclear] = Unclear

=== customer-discovery
name: Customer discovery
category: Startups and entrepreneurship
icon: person_search
description: Track discovery conversations and patterns.
## Discovery
text segment "Segment" *
table conversations "Conversations" [Who|Date|Key insight]
list patterns "Patterns"

=== founder-journal
name: Founder journal
category: Startups and entrepreneurship
icon: book
description: A founder's daily reflection.
title: Founder journal — {{date}}
## Today
list progress "Progress"
list worries "Worries"
long decision "Hardest decision"
rating energy "Energy" max=5

=== pitch-outline
name: Pitch outline
category: Startups and entrepreneurship
icon: present_to_all
description: The story arc of a pitch.
## Pitch
text company "Company" *
long problem "Problem"
long solution "Solution"
text market "Market"
text traction "Traction"
text model "Business model"
text team "Team"
text ask "The ask"

=== investor-research
name: Investor research
category: Startups and entrepreneurship
icon: account_balance
description: Fit of an investor: thesis, portfolio and contacts.
## Investor
text investor "Investor" *
text thesis "Thesis"
list portfolio "Relevant portfolio"
text cheque "Typical cheque"
text intro "Warm intro path"

=== fundraising-tracker
name: Fundraising tracker
category: Startups and entrepreneurship
icon: request_quote
description: Pipeline of investor conversations.
## Round
text round "Round" *
currency target "Target"
table pipeline "Pipeline" [Investor|Stage|Next step|Amount]

=== product-market-fit-observations
name: Product-market fit observations
category: Startups and entrepreneurship
icon: query_stats
description: Signals for and against product-market fit.
## Observations
text period "Period" *
list signals_for "Signals for"
list signals_against "Signals against"
text sean_ellis "Very disappointed % (survey)"

=== go-to-market-plan
name: Go-to-market plan
category: Startups and entrepreneurship
icon: storefront
description: Target segment, channels and launch plan.
## GTM
text product "Product" *
text segment "Beachhead segment"
text positioning "Positioning"
list channels "Channels"
text pricing "Pricing"
list milestones "Milestones"

=== hiring-plan
name: Hiring plan
category: Startups and entrepreneurship
icon: group_add
description: Roles to hire, when and why.
## Plan
table roles "Roles" [Role|When|Why|Budget]
long principles "Hiring principles"

=== partnership-research
name: Partnership research
category: Startups and entrepreneurship
icon: handshake
description: Assess a potential partner.
## Partner
text partner "Partner" *
text value "Mutual value"
list risks "Risks"
text contact "Contact"

=== startup-retrospective
name: Startup retrospective
category: Startups and entrepreneurship
icon: replay
description: Look back on a phase of the company.
## Retro
text phase "Phase" *
list right "What we got right"
list wrong "What we got wrong"
list next "What we'll do differently"

=== business-operating-plan
name: Business operating plan
category: Startups and entrepreneurship
icon: business_center
description: Annual plan: goals, budget, team and metrics.
## Plan
text year "Year" *
list goals "Company goals"
table budget "Budget" [Area|Amount]
list metrics "Metrics"
list risks "Risks"
