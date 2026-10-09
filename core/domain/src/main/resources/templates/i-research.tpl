#! I. Research and evidence — keep observation and interpretation separate.

=== research-brief
name: Research brief
category: Research and evidence
icon: travel_explore
description: Scope a piece of research: question, why, method and deliverable.
aliases: AI project brief
## Brief
text title_line "Research topic" *
long question "Research question"
long why "Why it matters"
text method "Approach"
date due "Due"
list deliverables "Deliverables"

=== literature-review
name: Literature review
category: Research and evidence
icon: local_library
description: Synthesise sources by theme, noting agreements and gaps.
## Review
text topic "Topic" *
table sources "Sources" [Source|Year|Key finding|Method]
list themes "Themes"
list disagreements "Disagreements"
list gaps "Gaps in the literature"

=== paper-summary
name: Paper summary
category: Research and evidence
icon: article
description: Summarise an academic paper in a structured way.
aliases: Research article notes
## Paper
text paper_title "Title" *
text authors "Authors"
text year "Year"
url link "Link / DOI"
long question "Question"
long method "Method"
long findings "Findings"
list limitations "Limitations"
long my_take "My take"

=== research-question
name: Research question
category: Research and evidence
icon: help
description: Refine a research question until it is answerable.
## Question
text question "Research question" *
long background "Background"
list sub_questions "Sub-questions"
text answerable "What evidence would answer it?"

=== hypothesis
name: Hypothesis
category: Research and evidence
icon: emoji_objects
description: State a falsifiable hypothesis and how to test it.
aliases: Experiment hypothesis, A/B test hypothesis
## Hypothesis
text hypothesis "We believe that…" *
text because "…because"
text measure "We'll know we're right when"
text falsify "It would be wrong if"
select status "Status" [Untested|Supported|Refuted|Inconclusive] = Untested

=== methodology
name: Methodology
category: Research and evidence
icon: schema
description: Document how a study is designed and why.
## Method
text study "Study" *
long design "Design"
long sample "Sample / participants"
long instruments "Instruments / data collection"
long analysis "Analysis plan"
list ethics "Ethical considerations"

=== experiment-design
name: Experiment design
category: Research and evidence
icon: science
description: Variables, controls and procedure for an experiment.
## Design
text experiment "Experiment" *
text independent "Independent variable"
text dependent "Dependent variable"
list controls "Controls"
long procedure "Procedure"
number sample_size "Sample size" min=0

=== experiment-log
name: Experiment log
category: Research and evidence
icon: edit_calendar
description: Running log of experiment runs and results.
## Runs
text experiment "Experiment" *
table runs "Runs" [Date|Conditions|Result|Notes]
long anomalies "Anomalies"

=== observation-log
name: Observation log
category: Research and evidence
icon: visibility
description: Timestamped observations kept separate from interpretation.
aliases: Usability observation, Production observation, Inventory observation
## Context
text context "Setting / subject" *
## Observation +repeat
datetime when "When" = now
long observed "What I observed (facts only)" *
long interpretation "My interpretation"

=== evidence-register
name: Evidence register
category: Research and evidence
icon: inventory
description: Track evidence, its source and strength.
aliases: Audit evidence index
## Evidence
text claim_area "Area / claim" *
table evidence "Evidence" [Evidence|Source|Strength H/M/L|Supports or refutes]

=== source-evaluation
name: Source evaluation
category: Research and evidence
icon: verified_user
description: Assess credibility of a source before relying on it.
## Source
text source "Source" *
url link "Link"
select type "Type" [Peer-reviewed|Government|News|Industry|Blog|Other] = Other
rating credibility "Credibility" max=5
long authority "Author authority"
long bias "Possible bias"
long currency_note "Currency (is it up to date?)"

=== citation-record
name: Citation record
category: Research and evidence
icon: format_quote
description: Full citation details plus where and why you used it.
## Citation
text citation_title "Title" *
text authors "Authors"
text year "Year"
text publisher "Publisher / journal"
url link "URL / DOI"
text pages "Pages"
long used_for "Used for"

=== annotated-bibliography
name: Annotated bibliography
category: Research and evidence
icon: list
description: Sources with a short summary and evaluation each.
## Entry +repeat
text citation "Citation" *
long summary "Summary"
long evaluation "Evaluation / relevance"

=== interview-protocol
name: Interview protocol
category: Research and evidence
icon: forum
description: A guide for consistent research interviews.
## Protocol
text study "Study" *
long intro "Introduction script"
checklist consent "Consent steps" = "Explain purpose|Ask permission to record|Explain how data is stored"
list questions "Questions"
list probes "Probes"
long closing "Closing script"

=== field-notes
name: Field notes
category: Research and evidence
icon: hiking
description: Notes from the field: setting, observations and reflections.
## Field visit
text place "Place" *
datetime when "When" = now
long setting "Setting"
long observations "Observations"
long reflections "Reflections (interpretation)"

=== data-dictionary
name: Data dictionary
category: Research and evidence
icon: dataset
description: Definitions for every field in a dataset.
## Dataset
text dataset "Dataset" *
text owner "Owner / source"
table fields "Fields" [Field|Type|Description|Allowed values|Example]

=== research-findings
name: Research findings
category: Research and evidence
icon: insights
description: Findings with supporting evidence and confidence.
## Findings
text study "Study" *
## Finding +repeat
text finding "Finding" *
long evidence "Evidence"
select confidence "Confidence" [High|Medium|Low] = Medium

=== limitations
name: Limitations
category: Research and evidence
icon: block
description: Be explicit about what the research can and cannot say.
aliases: Model limitation notes
## Limitations
text study "Study / model" *
list limitations "Limitations"
list impact "Impact on conclusions"
list mitigations "Mitigations"

=== reproducibility-checklist
name: Reproducibility checklist
category: Research and evidence
icon: replay
description: Make sure someone (including future you) can repeat the work.
## Checklist
text project "Project" *
checklist items "Items" = "Data source recorded|Data version pinned|Code version recorded|Environment documented|Random seeds fixed|Steps to reproduce written|Outputs archived"

=== research-synthesis
name: Research synthesis
category: Research and evidence
icon: merge
description: Combine findings across sources into themes and implications.
aliases: Synthesis note
## Synthesis
text topic "Topic" *
table themes "Themes" [Theme|Evidence|Sources]
long implications "Implications"
list open "Open questions"

=== research-retrospective
name: Research retrospective
category: Research and evidence
icon: history
description: Reflect on how a research project went.
## Retro
text project "Project" *
list went_well "Went well"
list improve "To improve"
list methods "Method lessons"
