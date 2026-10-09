#! AE. Spirituality and personal reflection

=== meditation-journal
name: Meditation journal
category: Spirituality and personal reflection
icon: self_improvement
description: Log meditation practice and what you noticed.
title: Meditation — {{date}}
## Session
number minutes "Minutes" min=0
text practice "Practice / technique"
rating settledness "Settledness" max=5
long noticed "What I noticed"

=== contemplation-journal
name: Prayer or contemplation journal
category: Spirituality and personal reflection
icon: spa
description: Space for prayer, contemplation and gratitude in your own tradition.
aliases: Contemplation journal
title: Contemplation — {{date}}
## Entry
text focus "Focus / text"
long reflection "Reflection" *
list intentions "Intentions"
list gratitude "Gratitude"

=== life-lessons
name: Life lessons
category: Spirituality and personal reflection
icon: lightbulb
description: Lessons life has taught you, with the story behind each.
## Lesson +repeat
text lesson "Lesson" *
long story "The story behind it"

=== personal-commitments
name: Personal commitments
category: Spirituality and personal reflection
icon: handshake
description: Commitments you make to yourself and how you'll keep them.
## Commitments
table commitments "Commitments" [Commitment|Why|How I'll keep it]
date review "Review on"
