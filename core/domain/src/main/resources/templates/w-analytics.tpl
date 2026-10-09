#! W. Analytics and data science

=== analytics-question
name: Analytics question
category: Analytics and data science
icon: query_stats
description: Frame an analytics question with the decision it informs.
## Question
text question "Question" *
text decision "Decision it informs"
list data "Data needed"
text method "Method"
long answer "Answer"

=== kpi-definition
name: KPI definition
category: Analytics and data science
icon: speed
description: Precise definition of a KPI.
aliases: Metric specification
## KPI
text kpi "KPI" *
long definition "Definition"
text formula "Formula"
text source "Data source"
text owner "Owner"
text target "Target"

=== dashboard-review
name: Dashboard review
category: Analytics and data science
icon: dashboard
description: Review a dashboard's usefulness and accuracy.
## Review
text dashboard "Dashboard" *
list used_for "Used for"
list issues "Issues"
list improvements "Improvements"

=== data-quality-issue
name: Data quality issue
category: Analytics and data science
icon: error
description: Document a data quality problem and its fix.
## Issue
text dataset "Dataset" *
select dimension "Dimension" [Completeness|Accuracy|Consistency|Timeliness|Uniqueness|Validity] = Accuracy
long description "Description"
text impact "Impact"
text fix "Fix"

=== data-profiling
name: Data profiling
category: Analytics and data science
icon: table_rows
description: Profile columns: types, nulls, ranges.
## Profile
text dataset "Dataset" *
number rows "Row count" min=0
table columns "Columns" [Column|Type|Null %|Distinct|Min|Max]

=== sql-query-notes
name: SQL query notes
category: Analytics and data science
icon: code
description: A query, what it does and caveats.
## Query
text purpose "Purpose" *
long query "Query"
list caveats "Caveats"
text database "Database"

=== python-experiment
name: Python experiment
category: Analytics and data science
icon: terminal
description: Notebook-style experiment record.
## Experiment
text goal "Goal" *
text environment "Environment / versions"
long code "Key code"
long results "Results"
text next "Next"

=== statistical-analysis
name: Statistical analysis
category: Analytics and data science
icon: functions
description: Hypotheses, tests, results and interpretation.
## Analysis
text question "Question" *
text h0 "Null hypothesis"
text test "Test used"
text result "Result (statistic, p, CI)"
long interpretation "Interpretation"
list assumptions "Assumptions checked"

=== model-evaluation
name: Model evaluation
category: Analytics and data science
icon: model_training
description: How a model performs and where it fails.
## Evaluation
text model "Model" *
table metrics "Metrics" [Metric|Train|Validation|Test]
list failure_modes "Failure modes"
list fairness "Fairness checks"
select decision "Decision" [Ship|Iterate|Reject] = Iterate

=== data-pipeline-incident
name: Data pipeline incident
category: Analytics and data science
icon: plumbing
description: A pipeline failure, its blast radius and fix.
## Incident
text pipeline "Pipeline" *
datetime detected "Detected"
long impact "Downstream impact"
text cause "Cause"
checklist actions "Actions"

=== data-lineage-notes
name: Data lineage notes
category: Analytics and data science
icon: account_tree
description: Where data comes from and where it goes.
## Lineage
text dataset "Dataset" *
list upstream "Upstream sources"
list transforms "Transformations"
list downstream "Downstream consumers"

=== dashboard-requirements
name: Dashboard requirements
category: Analytics and data science
icon: space_dashboard
description: Audience, questions and metrics for a new dashboard.
## Requirements
text dashboard "Dashboard" *
text audience "Audience"
list questions "Questions it must answer"
list metrics "Metrics"
text refresh "Refresh frequency"

=== insight-summary
name: Insight summary
category: Analytics and data science
icon: tips_and_updates
description: An insight, evidence and recommended action.
## Insight
text insight "Insight" *
long evidence "Evidence"
text so_what "So what"
text action "Recommended action"

=== forecast-assumptions
name: Forecast assumptions
category: Analytics and data science
icon: timeline
description: Assumptions behind a forecast and their sensitivity.
aliases: Financial assumptions
## Forecast
text forecast "Forecast" *
table assumptions "Assumptions" [Assumption|Value|Sensitivity|Source]

=== business-performance-review
name: Business performance review
category: Analytics and data science
icon: monitoring
description: Performance against targets and the story behind it.
## Review
text period "Period" *
table kpis "KPIs" [KPI|Target|Actual|Comment]
long story "Narrative"
list actions "Actions"

=== analytics-retrospective
name: Analytics retrospective
category: Analytics and data science
icon: replay
description: Reflect on an analytics project.
## Retro
text project "Project" *
list impact "Impact achieved"
list well "What went well"
list improve "What to improve"
