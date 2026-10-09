#! AB. AI and prompt engineering

=== prompt-library-entry
name: Prompt library entry
category: AI and prompt engineering
icon: terminal
description: A reusable prompt with its purpose, variables and known results.
aliases: Prompt version history
## Prompt
text prompt_name "Name" *
text purpose "Purpose"
long prompt "Prompt"
list variables "Variables"
text model "Model(s) tested"
table versions "Versions" [Version|Change|Result]

=== system-prompt-design
name: System prompt design
category: AI and prompt engineering
icon: settings_suggest
description: Role, rules, format and examples for a system prompt.
## Design
text assistant "Assistant" *
long role "Role and goal"
list rules "Rules"
long format "Output format"
list examples "Examples"
list edge_cases "Edge cases"

=== prompt-experiment
name: Prompt experiment
category: AI and prompt engineering
icon: science
description: Compare prompt variants on the same inputs.
## Experiment
text question "Question" *
table runs "Runs" [Variant|Input|Output summary|Score]
text conclusion "Conclusion"

=== model-comparison
name: Model comparison
category: AI and prompt engineering
icon: compare
description: Compare models on quality, cost and latency for a task.
## Comparison
text task "Task" *
table models "Models" [Model|Quality|Cost|Latency|Notes]
text choice "Choice"

=== evaluation-rubric
name: Evaluation rubric
category: AI and prompt engineering
icon: grading
description: Criteria and scales for judging outputs consistently.
## Rubric
text task "Task" *
table criteria "Criteria" [Criterion|1 = poor|3 = adequate|5 = excellent]

=== ai-workflow
name: AI workflow
category: AI and prompt engineering
icon: account_tree
description: Steps where AI assists and where humans check.
aliases: Automation specification
## Workflow
text workflow "Workflow" *
table steps "Steps" [Step|AI or human|Tool|Check]
list risks "Risks"

=== agent-specification
name: Agent specification
category: AI and prompt engineering
icon: smart_toy
description: Goal, tools, permissions and stop conditions for an agent.
## Agent
text agent "Agent" *
long goal "Goal"
list tools "Tools"
list permissions "Allowed actions"
list forbidden "Forbidden actions"
list stop "Stop / escalate when"

=== hallucination-investigation
name: Hallucination investigation
category: AI and prompt engineering
icon: report
description: Analyse an incorrect AI output and how to prevent it.
## Investigation
long prompt "Prompt"
long output "Output"
long truth "What was actually true"
text cause "Likely cause"
list mitigations "Mitigations"

=== retrieval-evaluation
name: Retrieval evaluation
category: AI and prompt engineering
icon: manage_search
description: Check that retrieval returns the right sources.
aliases: Knowledge-grounding test
## Evaluation
text system "System" *
table queries "Queries" [Query|Expected source|Retrieved?|Answer grounded?]

=== prompt-injection-test
name: Prompt injection test
category: AI and prompt engineering
icon: gpp_maybe
description: Test whether untrusted content can override instructions.
## Test
text system "System" *
table cases "Cases" [Injected content|Expected behaviour|Actual|Pass?]

=== ai-privacy-assessment
name: AI privacy assessment
category: AI and prompt engineering
icon: privacy_tip
description: What data an AI feature sends, where and with what consent.
aliases: Privacy impact notes
## Assessment
text feature "Feature / system" *
list data "Data sent"
text destination "Where it goes"
text retention "Retention"
text consent "Consent mechanism"
list mitigations "Mitigations"
