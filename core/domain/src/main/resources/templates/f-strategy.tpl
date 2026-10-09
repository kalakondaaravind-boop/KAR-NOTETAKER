#! F. Strategy and management consulting

=== executive-summary
name: Executive summary
category: Strategy and management consulting
icon: summarize
description: Lead with the answer: situation, recommendation, rationale and next steps.
## Summary
text topic "Topic" *
long answer "The answer in one or two sentences" *
long situation "Situation"
long complication "Complication"
list rationale "Supporting points"
checklist next_steps "Next steps"

=== problem-statement
name: Problem statement
category: Strategy and management consulting
icon: report_problem
description: Frame the problem precisely before solving it.
## Problem
long question "Key question to answer" * ? "How might we…?"
long context "Context"
text decision_maker "Decision maker"
list success "Criteria for success"
list constraints "Constraints"
list scope_out "Out of scope"

=== issue-tree
name: Issue tree
category: Strategy and management consulting
icon: account_tree
description: Break a question into mutually exclusive, collectively exhaustive sub-questions.
aliases: MECE issue breakdown
## Root
text question "Key question" *
## Branch +repeat
text branch "Sub-question" *
list sub_issues "Sub-issues"
text analysis "Analysis needed"
## Check
checkbox mece "Branches are mutually exclusive and collectively exhaustive"

=== hypothesis-tree
name: Hypothesis tree
category: Strategy and management consulting
icon: psychology_alt
description: State a governing hypothesis and the supporting hypotheses that must be true.
## Governing hypothesis
text hypothesis "Governing hypothesis" *
## Supporting hypothesis +repeat
text supporting "Must be true" *
text test "How we'll test it"
select status "Status" [Untested|Supported|Refuted|Mixed] = Untested

=== situation-assessment
name: Situation assessment
category: Strategy and management consulting
icon: radar
description: A structured read of where things stand today.
## Assessment
text subject "Organisation / unit" *
long overview "Overview"
list strengths "What's working"
list problems "What's not working"
table metrics "Key metrics" [Metric|Value|Trend]
long implications "Implications"

=== current-state-assessment
name: Current-state assessment
category: Strategy and management consulting
icon: fact_check
description: Document processes, people, technology and data as they are now.
## Current state
text scope "Scope" *
long process "Process"
long people "People and organisation"
long technology "Technology"
long data "Data"
list pain_points "Pain points"

=== future-state-vision
name: Future-state vision
category: Strategy and management consulting
icon: visibility
description: Describe the target state and how you'll know you've reached it.
## Vision
text scope "Scope" *
long vision "Vision statement"
list principles "Design principles"
long process "Future process"
list kpis "Success indicators"
date horizon "Target date"

=== gap-analysis
name: Gap analysis
category: Strategy and management consulting
icon: compare_arrows
description: Compare current and target state and list actions to close gaps.
## Gaps
text scope "Scope" *
table gaps "Gaps" [Dimension|Current|Target|Gap|Action]
list priorities "Priority gaps"

=== swot-analysis
name: SWOT analysis
category: Strategy and management consulting
icon: grid_on
description: Strengths, weaknesses, opportunities and threats with implications.
## Subject
text subject "Subject of analysis" *
## Internal
list strengths "Strengths"
list weaknesses "Weaknesses"
## External
list opportunities "Opportunities"
list threats "Threats"
## So what
long implications "Strategic implications"

=== pestle-analysis
name: PESTLE analysis
category: Strategy and management consulting
icon: public
description: Political, economic, social, technological, legal and environmental factors.
## Scope
text subject "Market / organisation" *
## Factors
list political "Political"
list economic "Economic"
list social "Social"
list technological "Technological"
list legal "Legal"
list environmental "Environmental"
long implications "Implications"

=== porters-five-forces
name: Porter's Five Forces
category: Strategy and management consulting
icon: hub
description: Assess industry attractiveness through five competitive forces.
## Industry
text industry "Industry" *
## Forces
table forces "Forces" [Force|Strength Low/Med/High|Drivers] = "Rivalry among competitors;;|Threat of new entrants;;|Threat of substitutes;;|Buyer power;;|Supplier power;;"
long conclusion "Overall attractiveness"

=== value-chain-analysis
name: Value chain analysis
category: Strategy and management consulting
icon: linear_scale
description: Where value and cost are created across primary and support activities.
## Value chain
text company "Company" *
table primary "Primary activities" [Activity|Value created|Cost|Advantage?] = "Inbound logistics;;;|Operations;;;|Outbound logistics;;;|Marketing and sales;;;|Service;;;"
table support "Support activities" [Activity|Notes] = "Infrastructure;|HR;|Technology;|Procurement;"
long insights "Insights"

=== business-model-canvas
name: Business model canvas
category: Strategy and management consulting
icon: view_quilt
description: The nine building blocks of a business model.
aliases: Business model
## Canvas
text business "Business" *
list segments "Customer segments"
list value_props "Value propositions"
list channels "Channels"
list relationships "Customer relationships"
list revenue "Revenue streams"
list resources "Key resources"
list activities "Key activities"
list partners "Key partners"
list costs "Cost structure"

=== competitive-analysis
name: Competitive analysis
category: Strategy and management consulting
icon: leaderboard
description: Compare competitors on the dimensions customers care about.
aliases: Competitive landscape
## Market
text market "Market" *
table competitors "Competitors" [Competitor|Positioning|Strengths|Weaknesses|Price]
long differentiation "Our differentiation"
list watch "Signals to watch"

=== market-sizing
name: Market sizing
category: Strategy and management consulting
icon: pie_chart
description: Estimate market size top-down and bottom-up with explicit assumptions.
## Market
text market "Market" *
long top_down "Top-down estimate"
long bottom_up "Bottom-up estimate"
table assumptions "Assumptions" [Assumption|Value|Source]
currency estimate "Estimated size"

=== tam-sam-som-analysis
name: TAM/SAM/SOM analysis
category: Strategy and management consulting
icon: donut_large
description: Total, serviceable and obtainable market with reasoning.
## Market
text market "Market" *
currency tam "TAM — total addressable"
currency sam "SAM — serviceable available"
currency som "SOM — serviceable obtainable"
long method "Method and sources"

=== growth-strategy
name: Growth strategy
category: Strategy and management consulting
icon: trending_up
description: Choose growth vectors and the bets that support them.
## Growth
text business "Business" *
table vectors "Growth options" [Option|Type|Potential|Risk]
list bets "Bets we're making"
list capabilities "Capabilities required"
list kpis "KPIs"

=== operating-model
name: Operating model
category: Strategy and management consulting
icon: settings_suggest
description: How the organisation delivers its strategy.
## Operating model
text scope "Organisation / unit" *
long structure "Structure"
long processes "Core processes"
long people "People and skills"
long technology "Technology and data"
long governance "Governance and performance"

=== target-operating-model
name: Target operating model
category: Strategy and management consulting
icon: architecture
description: The designed future operating model and transition plan.
## TOM
text scope "Scope" *
table design "Design by dimension" [Dimension|Today|Target]
list principles "Design principles"
list transitions "Transition steps"
list risks "Risks"

=== capability-assessment
name: Capability assessment
category: Strategy and management consulting
icon: equalizer
description: Rate capability maturity against what the strategy needs.
## Capabilities
text scope "Scope" *
table caps "Capabilities" [Capability|Current maturity 1–5|Required 1–5|Gap|Action]

=== strategic-options
name: Strategic options
category: Strategy and management consulting
icon: alt_route
description: Lay out distinct strategic options before choosing.
aliases: Options evaluation
## Context
text question "Strategic question" *
## Option +repeat
text option "Option" *
long description "Description"
list pros "Pros"
list cons "Cons"
text cost "Cost / effort"
## Choice
long recommendation "Recommendation"

=== decision-matrix
name: Decision matrix
category: Strategy and management consulting
icon: table_view
description: Weighted scoring of options against criteria.
aliases: Concept evaluation, Vendor comparison, Purchase comparison
## Decision
text decision "Decision" *
table criteria "Criteria and weights" [Criterion|Weight %]
table scores "Scores" [Option|Criterion|Score 1–5]
long result "Result and sense check"

=== business-case
name: Business case
category: Strategy and management consulting
icon: work
description: Justify an investment with options, costs, benefits and risks.
## Case
text initiative "Initiative" *
long problem "Problem / opportunity"
list options "Options considered"
currency cost "Total cost"
currency benefit "Expected benefit"
number payback "Payback period (months)" min=0
list risks "Risks"
long recommendation "Recommendation"

=== cost-benefit-analysis
name: Cost-benefit analysis
category: Strategy and management consulting
icon: calculate
description: List and quantify costs and benefits over time.
## Analysis
text initiative "Initiative" *
table costs "Costs" [Cost|One-off|Recurring]
table benefits "Benefits" [Benefit|Value|Confidence]
long conclusion "Conclusion"

=== benefits-realization
name: Benefits realization
category: Strategy and management consulting
icon: savings
description: Track whether promised benefits actually materialise.
## Benefits
text programme "Programme" *
table benefits "Benefits" [Benefit|Baseline|Target|Actual|Owner]
date review "Next review"

=== transformation-roadmap
name: Transformation roadmap
category: Strategy and management consulting
icon: route
description: Sequenced waves of change with milestones.
## Roadmap
text programme "Programme" *
table waves "Waves" [Wave|Timing|Initiatives|Milestone]
list dependencies "Critical dependencies"
list risks "Risks"

=== change-impact-assessment
name: Change impact assessment
category: Strategy and management consulting
icon: published_with_changes
description: Who is affected by a change, how much, and what support they need.
## Change
text change "Change" *
table impacts "Impacts" [Group|What changes|Impact H/M/L|Support needed]
list comms "Communication plan"

=== stakeholder-analysis
name: Stakeholder analysis
category: Strategy and management consulting
icon: groups_2
description: Map stakeholders by influence and interest and plan engagement.
## Stakeholders
text initiative "Initiative" *
table map "Stakeholder map" [Stakeholder|Influence H/M/L|Interest H/M/L|Stance|Approach]

=== governance-model
name: Governance model
category: Strategy and management consulting
icon: account_balance
description: Forums, decision rights and escalation paths.
## Governance
text scope "Programme / organisation" *
table forums "Forums" [Forum|Purpose|Cadence|Chair]
table rights "Decision rights" [Decision|Who decides|Who is consulted]
long escalation "Escalation path"

=== executive-briefing
name: Executive briefing
category: Strategy and management consulting
icon: business
description: A crisp briefing for a senior leader.
## Briefing
text topic "Topic" *
text audience "Audience"
long bottom_line "Bottom line"
list key_points "Key points"
list asks "Decisions requested"
list risks "Risks"

=== board-briefing
name: Board briefing
category: Strategy and management consulting
icon: account_balance_wallet
description: Prepare material for a board discussion or decision.
## Board paper
text topic "Topic" *
date meeting "Board date"
select purpose "Purpose" [For decision|For discussion|For information] = "For discussion"
long summary "Summary"
list options "Options"
long recommendation "Recommendation"
list risks "Risks and mitigations"

=== steering-committee-briefing
name: Steering committee briefing
category: Strategy and management consulting
icon: directions
description: Programme progress and decisions needed for a steering committee.
## SteerCo
text programme "Programme" *
date meeting "Meeting date"
select rag "Status" [Green|Amber|Red] = Green
list progress "Progress since last meeting"
list decisions "Decisions required"
list escalations "Escalations"

=== workshop-design
name: Workshop design
category: Strategy and management consulting
icon: design_services
description: Design a workshop: objectives, agenda, exercises and materials.
aliases: Workshop plan
## Design
text workshop "Workshop" *
list objectives "Objectives"
text participants "Participants"
table agenda "Agenda" [Time|Session|Method|Output]
list materials "Materials"

=== hypothesis-tracker
name: Hypothesis tracker
category: Strategy and management consulting
icon: science
description: Track hypotheses, evidence and conclusions across an engagement.
## Hypotheses
text engagement "Engagement" *
table hyps "Hypotheses" [Hypothesis|Evidence for|Evidence against|Status]

=== recommendation-register
name: Recommendation register
category: Strategy and management consulting
icon: recommend
description: Log recommendations, their status and owners.
## Recommendations
text scope "Scope" *
table recs "Recommendations" [Recommendation|Priority|Owner|Status|Date]

=== implementation-plan
name: Implementation plan
category: Strategy and management consulting
icon: engineering
description: Turn a decision into actions, owners and dates.
## Plan
text initiative "Initiative" *
table actions "Workstreams" [Workstream|Actions|Owner|Due]
list risks "Risks"
list success "Success measures"

=== value-creation-plan
name: Value creation plan
category: Strategy and management consulting
icon: paid
description: Levers that will create value, with targets and owners.
## Plan
text business "Business" *
table levers "Value levers" [Lever|Initiative|Target impact|Owner|Timing]
long thesis "Investment / value thesis"

=== post-implementation-review
name: Post-implementation review
category: Strategy and management consulting
icon: rate_review
description: Did the change deliver what was promised?
## Review
text initiative "Initiative" *
long objectives "Original objectives"
long results "Results achieved"
table metrics "Metrics" [Metric|Target|Actual]
list lessons "Lessons"
list follow_up "Follow-up actions"
