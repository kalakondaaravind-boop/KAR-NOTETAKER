#! B. Journaling and reflection

=== daily-journal
name: Daily journal
category: Journaling and reflection
icon: book
description: A short daily check-in: how you are, what happened, what matters.
title: Journal — {{date}}
tags: journal
aliases: Journal entry
## Today
date day "Date" = today
rating mood "Overall mood" max=5
long happened "What happened today"
long felt "How I felt about it"
list grateful "Grateful for"
text tomorrow "One thing for tomorrow"

=== morning-journal
name: Morning journal
category: Journaling and reflection
icon: wb_sunny
description: Start the day with intention: priorities, gratitude and mindset.
title: Morning — {{date}}
tags: journal, morning
## Morning
rating energy "Energy" max=5
list grateful "Three things I'm grateful for"
text intention "Today's intention" ? "How do I want to show up?"
checklist priorities "Priorities"
long on_mind "On my mind"

=== evening-journal
name: Evening journal
category: Journaling and reflection
icon: bedtime
description: Close the day: what went well, what to let go, what to carry forward.
title: Evening — {{date}}
tags: journal, evening
## Evening
list went_well "What went well"
long challenge "What was challenging"
long learned "What I learned"
list let_go "What I'm letting go of"
rating day_rating "Rate the day" max=5

=== gratitude-journal
name: Gratitude journal
category: Journaling and reflection
icon: favorite
description: Note specific things you're grateful for and why.
title: Gratitude — {{date}}
tags: gratitude
aliases: Gratitude reflection
## Gratitude +repeat
text thing "I'm grateful for" *
long why "Why it mattered"
## Kindness
text someone "Someone I appreciate today"

=== mood-journal
name: Mood journal
category: Journaling and reflection
icon: mood
description: Track mood, energy and context over time to notice patterns.
title: Mood — {{date}}
tags: mood
private: true
disclaimer: This journal is a personal record, not a diagnostic or treatment tool. If you are struggling, consider speaking with a qualified professional.
## Check-in
datetime when "When" = now
rating mood "Mood" max=10
rating energy "Energy" max=10
rating anxiety "Stress / anxiety" max=10
multiselect feelings "Feelings" [Calm|Content|Happy|Excited|Tired|Anxious|Sad|Frustrated|Overwhelmed|Lonely]
## Context
number sleep_hours "Hours slept" min=0 max=24
long context "What was going on"
long helped "What helped"

=== reflection
name: Reflection
category: Journaling and reflection
icon: self_improvement
description: Think through an experience: what happened, what it means, what next.
aliases: Personal reflection
## Reflection
text topic "What I'm reflecting on" *
long what "What happened"
long so_what "So what — why does it matter?"
long now_what "Now what — what will I do?"

=== wins-and-lessons
name: Wins and lessons
category: Journaling and reflection
icon: emoji_events
description: Celebrate progress and capture lessons while they are fresh.
title: Wins and lessons — {{date}}
## Wins
list wins "Wins (big or small)"
## Lessons
table lessons "Lessons" [What happened|Lesson|Next time I'll]

=== personal-retrospective
name: Personal retrospective
category: Journaling and reflection
icon: history
description: A retrospective on a period or project of your own life.
aliases: Personal career retrospective
## Scope
text period "Period or project" *
## Retro
list keep "Keep doing"
list stop "Stop doing"
list start "Start doing"
long surprise "What surprised me"
checklist actions "Actions"

=== free-writing-journal
name: Free-writing journal
category: Journaling and reflection
icon: edit_note
description: Write without stopping for a set time; reflect afterwards.
title: Free writing — {{date}}
aliases: Morning pages
## Session
number minutes "Minutes" = 10 min=1 max=120
text prompt "Prompt (optional)" ? "What I keep thinking about is…"
long writing "Writing" *
## Afterwards
text insight "One insight from the page"

=== travel-journal
name: Travel journal
category: Journaling and reflection
icon: flight_takeoff
description: Capture places, people and moments from a trip day.
title: Travel journal — {{date}}
tags: travel
## Day
text place "Where I am" *
date day "Date" = today
long highlights "Highlights"
list ate "What I ate"
list people "People I met"
long sensory "Sights, sounds, smells"
rating day_rating "Rating" max=5

=== memory-journal
name: Memory journal
category: Journaling and reflection
icon: photo_album
description: Preserve a memory in detail before it fades.
aliases: Meaningful experiences
## Memory
text title_line "Memory" *
date when "When"
text where "Where"
list who "Who was there"
long story "What happened"
long why "Why it matters to me"

=== dream-journal
name: Dream journal
category: Journaling and reflection
icon: nights_stay
description: Record dreams right after waking and look for recurring themes.
title: Dream — {{date}}
## Dream
date night "Night of" = today
long dream "The dream" *
multiselect tone "Tone" [Pleasant|Neutral|Unsettling|Vivid|Lucid|Recurring]
list symbols "Symbols and people"
long waking "Thoughts on waking"

=== reading-journal
name: Reading journal
category: Journaling and reflection
icon: auto_stories
description: Reflect on what you read today and how it connects to your life.
title: Reading — {{date}}
aliases: Reading reflection
## Reading
text source "What I read" *
number pages "Pages / minutes" min=0
long ideas "Ideas that stood out"
long connection "How it connects to my life"
list quotes "Lines worth keeping"

=== learning-journal
name: Learning journal
category: Journaling and reflection
icon: school
description: Log what you learned, how, and what still confuses you.
title: Learning — {{date}}
## Session
text topic "Topic" *
number minutes "Minutes spent" min=0
long learned "What I learned"
list confusing "Still unclear"
text next "What to study next"
rating confidence "Confidence" max=5

=== values-reflection
name: Values reflection
category: Journaling and reflection
icon: diamond
description: Check how closely recent choices matched your values.
aliases: Values journal
## Values
table alignment "Values and alignment" [Value|Where I lived it|Where I didn't]
long tension "A tension I'm noticing"
text adjust "One adjustment"

=== life-review
name: Life review
category: Journaling and reflection
icon: landscape
description: Reflect on chapters of your life and the threads running through them.
## Chapters +repeat
text chapter "Chapter" *
text years "Years"
long defining "Defining moments"
long learned "What it taught me"
## Threads
list threads "Recurring themes"
long legacy "What I want to be remembered for"

=== personal-growth-plan
name: Personal growth plan
category: Journaling and reflection
icon: trending_up
description: Choose growth areas, practices and how you'll measure progress.
## Growth areas +repeat
text area "Area" *
long why "Why now"
list practices "Practices"
text measure "How I'll know it's working"
date check_in "Check-in date"
