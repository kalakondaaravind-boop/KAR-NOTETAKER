#! Y. Career, hobbies, and personal interests

=== certification-tracker
name: Certification tracker
category: Career, hobbies, and personal interests
icon: workspace_premium
description: Certifications earned, in progress and renewal dates.
## Certifications
table certs "Certifications" [Certification|Status|Earned|Renew by]
date reviewed "Last reviewed" = today

=== portfolio-project
name: Portfolio project
category: Career, hobbies, and personal interests
icon: work
description: Document a project for your portfolio.
## Project
text project "Project" *
text role "My role"
long problem "Problem"
long approach "Approach"
long outcome "Outcome"
url link "Link"

=== hobby-project
name: Hobby project
category: Career, hobbies, and personal interests
icon: interests
description: Plan and log a hobby project.
## Project
text project "Project" *
list materials "Materials"
checklist steps "Steps"
currency spent "Spent"
long notes "Notes"

=== photography-log
name: Photography log
category: Career, hobbies, and personal interests
icon: photo_camera
description: Shoot details and what you learned.
## Shoot
text subject "Subject / location" *
date day "Date" = today
text gear "Gear"
text settings "Settings"
long learned "What I learned"

=== art-practice
name: Art practice
category: Career, hobbies, and personal interests
icon: brush
description: A practice session log for art.
## Practice
text medium "Medium" *
number minutes "Minutes" min=0
text focus "Focus"
long reflection "Reflection"

=== music-practice
name: Music practice
category: Career, hobbies, and personal interests
icon: piano
description: Practice log with goals and tempo.
## Practice
text instrument "Instrument" *
number minutes "Minutes" min=0
table pieces "Pieces" [Piece|Tempo|Notes]
text next "Next goal"

=== sports-practice
name: Sports practice
category: Career, hobbies, and personal interests
icon: sports_soccer
description: Drills, performance and next focus.
## Session
text sport "Sport" *
list drills "Drills"
rating performance "Performance" max=5
text next "Next focus"

=== gaming-notes
name: Gaming notes
category: Career, hobbies, and personal interests
icon: sports_esports
description: Progress, strategies and secrets for a game.
## Game
text game "Game" *
text progress "Progress"
list strategies "Strategies"
list todo "To do"

=== personal-challenges
name: Personal challenges
category: Career, hobbies, and personal interests
icon: military_tech
description: Self-set challenges with rules and progress.
## Challenge +repeat
text challenge "Challenge" *
text rules "Rules"
date ends "Ends"
progress progress "Progress"

=== bucket-list
name: Bucket list
category: Career, hobbies, and personal interests
icon: checklist
description: Things to do in life, with status.
## Bucket list
table items "Items" [Item|Category|Done?|Date]
long why "Why these matter"

=== volunteer-activity
name: Volunteer activity
category: Career, hobbies, and personal interests
icon: volunteer_activism
description: Record volunteering hours and impact.
## Activity
text organisation "Organisation" *
date day "Date" = today
number hours "Hours" min=0
long did "What I did"
text impact "Impact"

=== community-event-notes
name: Community event notes
category: Career, hobbies, and personal interests
icon: groups
description: Notes from a community event.
aliases: Event notes
## Event
text event "Event" *
date day "Date" = today
list people "People met"
list takeaways "Takeaways"

=== personal-knowledge-roadmap
name: Personal knowledge roadmap
category: Career, hobbies, and personal interests
icon: map
description: What you want to learn over the next year and in what order.
aliases: Topic learning plan
## Roadmap
table topics "Topics" [Topic|Why|When|Resources]
text first "Start with"
