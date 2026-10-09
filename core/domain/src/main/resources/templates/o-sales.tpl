#! O. Sales, marketing, and customer success

=== marketing-brief
name: Marketing brief
category: Sales, marketing, and customer success
icon: campaign
description: Objective, audience, message, channels and budget for a campaign.
## Brief
text campaign "Campaign" *
text objective "Objective"
text audience "Audience"
text message "Key message"
multiselect channels "Channels" [Email|Social|Search|Events|PR|Content|Partners|Paid]
currency budget "Budget"
list kpis "KPIs"

=== campaign-plan
name: Campaign plan
category: Sales, marketing, and customer success
icon: event_note
description: Timeline and assets for a campaign.
## Plan
text campaign "Campaign" *
date start "Start"
date end "End"
table assets "Assets" [Asset|Channel|Due|Status]
list metrics "Metrics"

=== campaign-retrospective
name: Campaign retrospective
category: Sales, marketing, and customer success
icon: replay
description: Results versus goals and lessons from a campaign.
## Retro
text campaign "Campaign" *
table results "Results" [Metric|Goal|Actual]
list worked "What worked"
list didnt "What didn't"

=== content-strategy
name: Content strategy
category: Sales, marketing, and customer success
icon: article
description: Goals, pillars, formats and distribution for content.
## Strategy
text brand "Brand / product" *
list goals "Goals"
list pillars "Content pillars"
list formats "Formats"
list distribution "Distribution"

=== seo-research
name: SEO research
category: Sales, marketing, and customer success
icon: search
description: Search intent, competitors and on-page plan for a topic.
## SEO
text topic "Topic" *
text intent "Search intent"
table competitors "Ranking pages" [URL|Strength|Gap]
list on_page "On-page plan"

=== keyword-research
name: Keyword research
category: Sales, marketing, and customer success
icon: key
description: Keywords with volume, difficulty and intent.
## Keywords
text topic "Topic" *
table keywords "Keywords" [Keyword|Volume|Difficulty|Intent]

=== competitor-messaging
name: Competitor messaging
category: Sales, marketing, and customer success
icon: record_voice_over
description: How competitors position themselves and where you differ.
## Messaging
text market "Market" *
table competitors "Competitors" [Competitor|Headline claim|Proof|Tone]
long whitespace "White space for us"

=== brand-positioning
name: Brand positioning
category: Sales, marketing, and customer success
icon: workspace_premium
description: For whom, what, and why you're different.
## Positioning
text brand "Brand" *
text for_whom "For"
text category "Category"
text benefit "Key benefit"
text unlike "Unlike"
text proof "Proof points"

=== value-proposition
name: Value proposition
category: Sales, marketing, and customer success
icon: diamond
description: Customer jobs, pains and gains mapped to your offer.
## Customer
text segment "Customer segment" *
list jobs "Jobs"
list pains "Pains"
list gains "Gains"
## Offer
list pain_relievers "Pain relievers"
list gain_creators "Gain creators"

=== lead-research
name: Lead research
category: Sales, marketing, and customer success
icon: person_search
description: Background on a lead before outreach.
aliases: Account research
## Lead
text company "Company" *
text contact "Contact / role"
url website "Website"
long context "Context and news"
list pains "Likely pains"
text angle "Outreach angle"

=== qualification-notes
name: Qualification notes
category: Sales, marketing, and customer success
icon: verified
description: BANT-style qualification of an opportunity.
## Qualification
text account "Account" *
text budget "Budget"
text authority "Authority"
text need "Need"
text timeline "Timeline"
select qualified "Qualified?" [Yes|No|Not yet] = "Not yet"

=== account-plan
name: Account plan
category: Sales, marketing, and customer success
icon: business
description: Goals, stakeholders and plays for a key account.
## Account
text account "Account" *
list goals "Account goals"
table stakeholders "Stakeholders" [Name|Role|Stance]
list opportunities "Opportunities"
list plays "Plays"

=== opportunity-review
name: Opportunity review
category: Sales, marketing, and customer success
icon: trending_up
description: Review a deal's health and next steps.
## Review
text opportunity "Opportunity" *
currency value "Value"
select stage "Stage" [Discovery|Proposal|Negotiation|Commit] = Discovery
list risks "Risks"
text next_step "Next step"
date close "Expected close"

=== objection-handling
name: Objection handling
category: Sales, marketing, and customer success
icon: question_answer
description: Common objections and tested responses.
## Objections
text product "Product" *
table objections "Objections" [Objection|Response|Proof]

=== proposal-outline
name: Proposal outline
category: Sales, marketing, and customer success
icon: request_page
description: Structure of a proposal from problem to price.
## Proposal
text client "Client" *
long problem "Their problem"
long solution "Proposed solution"
list deliverables "Deliverables"
table pricing "Pricing" [Item|Price]
text timeline "Timeline"

=== negotiation-preparation
name: Negotiation preparation
category: Sales, marketing, and customer success
icon: handshake
description: Interests, BATNA, walk-away point and concessions.
## Prep
text negotiation "Negotiation" *
list my_interests "My interests"
list their_interests "Their interests"
text batna "My best alternative (BATNA)"
text walk_away "Walk-away point"
list concessions "Possible concessions"

=== customer-onboarding
name: Customer onboarding
category: Sales, marketing, and customer success
icon: how_to_reg
description: Steps to get a new customer to first value.
## Onboarding
text customer "Customer" *
checklist steps "Steps" = "Kick-off call|Access set up|Training|First success milestone|Check-in"
text first_value "First value milestone"

=== customer-success-plan
name: Customer success plan
category: Sales, marketing, and customer success
icon: sentiment_satisfied
description: Desired outcomes and how you'll help the customer reach them.
## Plan
text customer "Customer" *
list outcomes "Desired outcomes"
table milestones "Milestones" [Milestone|Date|Status]
select health "Health" [Healthy|At risk|Critical] = Healthy

=== renewal-preparation
name: Renewal preparation
category: Sales, marketing, and customer success
icon: autorenew
description: Prepare for a renewal conversation.
## Renewal
text customer "Customer" *
date renewal "Renewal date"
list value "Value delivered"
list risks "Risks"
text proposal "Renewal proposal"

=== churn-analysis
name: Churn analysis
category: Sales, marketing, and customer success
icon: trending_down
description: Why customers leave and what to do about it.
## Churn
text period "Period" *
table reasons "Reasons" [Reason|Count|Example]
list actions "Actions"

=== case-study-outline
name: Case study outline
category: Sales, marketing, and customer success
icon: cases
description: Challenge, solution and results story.
## Case study
text customer "Customer" *
long challenge "Challenge"
long solution "Solution"
table results "Results" [Metric|Before|After]
text quote "Customer quote"

=== webinar-plan
name: Webinar plan
category: Sales, marketing, and customer success
icon: live_tv
description: Topic, speakers, agenda and promotion for a webinar.
## Webinar
text topic "Topic" *
datetime when "When"
list speakers "Speakers"
table agenda "Agenda" [Minutes|Segment]
list promotion "Promotion"

=== social-content
name: Social content
category: Sales, marketing, and customer success
icon: share
description: Draft social posts per platform.
## Post +repeat
select platform "Platform" [LinkedIn|X|Instagram|Facebook|TikTok|YouTube|Other] = LinkedIn
long copy "Copy" *
date publish "Publish date"

=== messaging-test
name: Messaging test
category: Sales, marketing, and customer success
icon: ab_testing
description: Compare message variants and results.
## Test
text audience "Audience" *
table variants "Variants" [Variant|Message|Result]
text winner "Winner"

=== marketing-experiment
name: Marketing experiment
category: Sales, marketing, and customer success
icon: science
description: A hypothesis-driven marketing test.
aliases: Pricing experiment
## Experiment
text hypothesis "Hypothesis" *
text metric "Primary metric"
text audience "Audience"
date start "Start"
long result "Result"
select decision "Decision" [Scale|Iterate|Stop] = Iterate
