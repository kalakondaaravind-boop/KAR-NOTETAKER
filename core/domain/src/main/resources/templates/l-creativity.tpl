#! L. Creativity and ideation

=== idea-expansion
name: Idea expansion
category: Creativity and ideation
icon: open_in_full
description: Develop a raw idea: who it's for, how it works, what could go wrong.
## Idea
text idea "Idea" *
text for_whom "Who it's for"
long how "How it works"
list variations "Variations"
list risks "What could go wrong"
text next "Smallest next test"

=== mind-map-outline
name: Mind map outline
category: Creativity and ideation
icon: hub
description: A central topic with branches and leaves, as an outline.
## Centre
text centre "Central topic" *
## Branch +repeat
text branch "Branch" *
list leaves "Leaves"

=== scamper-exercise
name: SCAMPER exercise
category: Creativity and ideation
icon: shuffle
description: Substitute, combine, adapt, modify, put to other use, eliminate, reverse.
## Subject
text subject "Product / process" *
## SCAMPER
list substitute "Substitute"
list combine "Combine"
list adapt "Adapt"
list modify "Modify / magnify"
list other_use "Put to another use"
list eliminate "Eliminate"
list reverse "Reverse / rearrange"

=== creative-brief
name: Creative brief
category: Creativity and ideation
icon: palette
description: Objective, audience, message and mandatories for creative work.
## Brief
text project "Project" *
text objective "Objective"
text audience "Audience"
long insight "Audience insight"
text message "Single-minded message"
list mandatories "Mandatories"
date due "Due"

=== design-inspiration
name: Design inspiration
category: Creativity and ideation
icon: collections
description: Collect references with what you like about each.
aliases: Mood board reference
## Reference +repeat
text reference "Reference" *
url link "Link"
text like "What I like about it"

=== problem-reframing
name: Problem reframing
category: Creativity and ideation
icon: crop_rotate
description: Look at a problem from several angles before solving it.
## Problem
text problem "Original problem" *
list reframes "Alternative framings"
long root "What's really going on?"
text chosen "Chosen framing"

=== what-if-exploration
name: What-if exploration
category: Creativity and ideation
icon: question_mark
description: Explore provocative what-if scenarios.
## Scenario +repeat
text what_if "What if…" *
long consequences "Consequences"
long opportunity "Opportunity"

=== analogy-collection
name: Analogy collection
category: Creativity and ideation
icon: compare
description: Borrow solutions from other domains.
## Analogies
text problem "Problem" *
table analogies "Analogies" [Domain|How they solve it|What I could borrow]

=== invention-notebook
name: Invention notebook
category: Creativity and ideation
icon: precision_manufacturing
description: Dated record of an invention idea, sketches and tests.
## Entry
text invention "Invention" *
date day "Date" = today
long description "Description"
list prior_art "Similar things that exist"
long tests "Tests and results"

=== creative-experiment
name: Creative experiment
category: Creativity and ideation
icon: experiment
description: Try a constraint or technique and capture what happened.
## Experiment
text experiment "Experiment" *
text constraint "Constraint / technique"
long result "Result"
text keep "What I'll keep"

=== feedback-record
name: Feedback record
category: Creativity and ideation
icon: feedback
description: Feedback received on your work and what you'll do with it.
aliases: Prototype feedback
## Feedback
text work "Work" *
text from "From"
date day "Date" = today
list positive "Positive"
list critical "Critical"
list actions "Actions"

=== idea-backlog
name: Idea backlog
category: Creativity and ideation
icon: inbox
description: A prioritised backlog of ideas.
## Backlog
text area "Area" *
table ideas "Ideas" [Idea|Value 1–5|Effort 1–5|Status]

=== innovation-pipeline
name: Innovation pipeline
category: Creativity and ideation
icon: filter_alt
description: Move ideas from discovery to scale through stage gates.
## Pipeline
text area "Area" *
table pipeline "Ideas by stage" [Idea|Stage|Evidence|Next gate]

=== prototype-notes
name: Prototype notes
category: Creativity and ideation
icon: build_circle
description: What a prototype tests and what you learned.
## Prototype
text prototype "Prototype" *
text question "Question it answers"
select fidelity "Fidelity" [Paper|Low|Medium|High] = Low
long result "Result"
list next "Next iteration"

=== creative-retrospective
name: Creative retrospective
category: Creativity and ideation
icon: palette
description: Reflect on a creative project and your process.
## Retro
text project "Project" *
long proud "What I'm proud of"
long struggle "Where I struggled"
list process "Process lessons"
