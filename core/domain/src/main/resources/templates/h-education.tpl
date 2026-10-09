#! H. Education and learning

=== class-notes
name: Class notes
category: Education and learning
icon: school
description: Cornell-style class notes: cues, notes and a summary.
title: {{field:course}} — {{date}}
aliases: Lecture notes
## Class
text course "Course" *
text topic "Topic"
date day "Date" = today
## Notes
long notes "Notes"
list cues "Cue questions / keywords"
long summary "Summary in my own words"

=== course-notes
name: Course notes
category: Education and learning
icon: library_books
description: An overview note for a whole course with modules and resources.
## Course
text course "Course" *
text provider "Provider / instructor"
url link "Course link"
## Module +repeat
text module "Module" *
long key_points "Key points"
checkbox done "Completed"

=== study-plan
name: Study plan
category: Education and learning
icon: calendar_view_week
description: Plan study sessions toward a goal or exam.
## Plan
text goal "Goal" *
date deadline "Deadline"
table sessions "Sessions" [Date|Topic|Minutes|Done?]
list resources "Resources"

=== revision-plan
name: Revision plan
category: Education and learning
icon: replay
description: Spread revision across topics by confidence.
## Revision
text subject "Subject" *
date exam "Exam date"
table topics "Topics" [Topic|Confidence 1–5|Next review|Method]

=== exam-preparation
name: Exam preparation
category: Education and learning
icon: quiz
description: Everything for an exam: format, topics, practice and logistics.
aliases: Certification preparation
## Exam
text exam "Exam" *
datetime when "Date and time"
text format "Format"
table topics "Topics" [Topic|Weight|Ready?]
list practice "Practice papers done"
checklist logistics "Logistics" = "Location confirmed|ID ready|Equipment ready"

=== flashcards
name: Flashcards
category: Education and learning
icon: style
description: Question-and-answer cards for active recall.
aliases: Question-and-answer study notes
## Deck
text deck "Deck" *
## Card +repeat
text front "Question" *
long back "Answer"

=== concept-explanation
name: Concept explanation
category: Education and learning
icon: psychology
description: Explain a concept clearly with an example and a counter-example.
aliases: Concept note
## Concept
text concept "Concept" *
long definition "Definition in plain language"
long example "Example"
long non_example "Counter-example"
list related "Related concepts"

=== feynman-technique
name: Feynman technique
category: Education and learning
icon: record_voice_over
description: Explain it simply, find the gaps, refine.
## Feynman
text topic "Topic" *
long simple "Explain it to a twelve-year-old"
list gaps "Where I got stuck"
long refined "Refined explanation"
text analogy "Analogy"

=== topic-summary
name: Topic summary
category: Education and learning
icon: short_text
description: Summarise a topic in layers: one line, a paragraph, key points.
aliases: Topic overview
## Summary
text topic "Topic" *
text one_line "One-line summary"
long paragraph "Paragraph summary"
list points "Key points"
list sources "Sources"

=== chapter-summary
name: Chapter summary
category: Education and learning
icon: menu_book
description: Summarise a textbook chapter for revision.
aliases: Chapter notes
## Chapter
text book "Book / course" *
text chapter "Chapter"
list key_ideas "Key ideas"
list terms "Key terms"
list questions "Questions to test myself"

=== assignment-plan
name: Assignment plan
category: Education and learning
icon: assignment
description: Break an assignment into steps with dates.
## Assignment
text assignment "Assignment" *
date due "Due"
long brief "Brief / requirements"
table steps "Steps" [Step|Target date|Done?]
list sources "Sources"

=== essay-outline
name: Essay outline
category: Education and learning
icon: subject
description: Thesis, argument structure and evidence.
aliases: Essay
## Essay
text title_line "Working title" *
long thesis "Thesis"
## Paragraph +repeat
text point "Point" *
long evidence "Evidence"
text link "Link back to thesis"
## Ending
long conclusion "Conclusion"

=== lab-notes
name: Lab notes
category: Education and learning
icon: biotech
description: Lab session record: aim, method, observations, results.
## Lab
text experiment "Experiment" *
date day "Date" = today
long aim "Aim"
long method "Method"
long observations "Observations"
table results "Results" [Measurement|Value|Unit]
long conclusion "Conclusion"

=== language-learning
name: Language learning
category: Education and learning
icon: translate
description: A session log for language practice.
title: {{field:language}} practice — {{date}}
## Session
text language "Language" *
select skill "Focus" [Speaking|Listening|Reading|Writing|Grammar|Vocabulary] = Vocabulary
number minutes "Minutes" min=0
list phrases "New phrases"
list mistakes "Mistakes to fix"

=== vocabulary-list
name: Vocabulary list
category: Education and learning
icon: abc
description: Words with meanings and example sentences.
aliases: Glossary
## Vocabulary
text topic "Topic / language" *
table words "Words" [Word|Meaning|Example]

=== spaced-repetition-record
name: Spaced repetition record
category: Education and learning
icon: event_repeat
description: Track review intervals for material you want to retain.
## Reviews
text material "Material" *
table reviews "Reviews" [Date|Recall 1–5|Next review]

=== learning-objective
name: Learning objective
category: Education and learning
icon: my_location
description: A clear learning objective with evidence of mastery.
## Objective
text objective "By the end I will be able to…" *
text measure "How I'll show it"
list resources "Resources"
date by "By"

=== course-review
name: Course review
category: Education and learning
icon: rate_review
description: Evaluate a course you took.
## Review
text course "Course" *
rating rating "Rating" max=5
long best "Best parts"
long worst "Weakest parts"
select recommend "Would I recommend it?" [Yes|No|Depends] = Yes

=== study-retrospective
name: Study retrospective
category: Education and learning
icon: history_edu
description: Look back on a study period and improve your approach.
## Retro
text period "Period" *
list worked "What worked"
list didnt "What didn't"
list change "What I'll change"
rating satisfaction "Satisfaction" max=5

=== knowledge-gap-list
name: Knowledge gap list
category: Education and learning
icon: help_center
description: Things you know you don't know yet, and how you'll learn them.
## Gaps
text area "Area" *
table gaps "Gaps" [Gap|Why it matters|How to learn|Priority]
