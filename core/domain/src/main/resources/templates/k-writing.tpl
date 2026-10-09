#! K. Writing and publishing

=== writing-brief
name: Writing brief
category: Writing and publishing
icon: edit_document
description: Purpose, audience, message and constraints before drafting.
## Brief
text piece "Piece" *
text audience "Audience"
long message "Core message"
text tone "Tone"
number words "Target words" min=0
date due "Due"

=== article-outline
name: Article outline
category: Writing and publishing
icon: format_list_bulleted
description: Hook, sections and conclusion for an article.
aliases: Long-form article
## Outline
text headline "Working headline" *
long hook "Hook"
## Section +repeat
text heading "Section heading" *
list points "Points"
## Ending
long conclusion "Conclusion / call to action"

=== blog-post
name: Blog post
category: Writing and publishing
icon: rss_feed
description: Draft a blog post with metadata for publishing.
## Post
text post_title "Title" *
text slug "Slug"
list keywords "Keywords"
text excerpt "Excerpt"
rich draft "Draft"
select status "Status" [Idea|Drafting|Editing|Published] = Drafting

=== newsletter
name: Newsletter
category: Writing and publishing
icon: mail
description: Plan and draft a newsletter issue.
title: Newsletter — {{date}}
## Issue
text issue "Issue / number" *
text subject "Subject line"
long intro "Intro"
table items "Items" [Heading|Link|Blurb]
long sign_off "Sign-off"

=== book-outline
name: Book outline
category: Writing and publishing
icon: import_contacts
description: Premise, structure and chapter plan for a book.
## Book
text working_title "Working title" *
long premise "Premise"
text audience "Audience"
table chapters "Chapters" [No.|Title|Purpose|Status]

=== chapter-draft
name: Chapter draft
category: Writing and publishing
icon: draft
description: Draft a chapter with its goal and notes.
## Chapter
text chapter "Chapter" *
text goal "What this chapter must do"
rich draft "Draft"
list notes "Revision notes"

=== story-concept
name: Story concept
category: Writing and publishing
icon: auto_awesome
description: The seed of a story: premise, protagonist, conflict and stakes.
## Concept
text logline "Logline" *
text genre "Genre"
text protagonist "Protagonist"
long conflict "Conflict"
long stakes "Stakes"
long ending "Possible ending"

=== character-profile
name: Character profile
category: Writing and publishing
icon: face
description: A fictional character's background, wants and flaws.
## Character
text name_field "Name" *
text role "Role in story"
long background "Background"
text want "What they want"
text need "What they need"
list traits "Traits"
text flaw "Flaw"
long arc "Arc"

=== worldbuilding
name: Worldbuilding
category: Writing and publishing
icon: public
description: Rules, places and cultures of a fictional world.
## World
text world "World" *
long rules "Rules (magic, technology, physics)"
table places "Places" [Place|Description]
table cultures "Cultures" [Culture|Values|Conflicts]
long history "History"

=== plot-outline
name: Plot outline
category: Writing and publishing
icon: timeline
description: Beats from setup to resolution.
## Plot
text story "Story" *
long setup "Setup"
long inciting "Inciting incident"
long rising "Rising action"
long midpoint "Midpoint"
long climax "Climax"
long resolution "Resolution"

=== scene-notes
name: Scene notes
category: Writing and publishing
icon: theaters
description: Purpose, conflict and outcome of a scene.
## Scene
text scene "Scene" *
text pov "Point of view"
text setting "Setting"
text goal "Character goal"
text conflict "Conflict"
text outcome "Outcome"
long notes "Notes"

=== dialogue-ideas
name: Dialogue ideas
category: Writing and publishing
icon: chat
description: Lines, voices and exchanges to use later.
## Dialogue
text story "Story / project" *
table lines "Lines" [Character|Line|Context]

=== research-for-writing
name: Research for writing
category: Writing and publishing
icon: manage_search
description: Facts and sources gathered for a piece of writing.
## Research
text piece "Piece" *
table facts "Facts" [Fact|Source|Verified?]
list questions "Still to find out"

=== editorial-review
name: Editorial review
category: Writing and publishing
icon: rate_review
description: Structured feedback on a draft.
aliases: Design critique, AI-generated content review
## Review
text piece "Piece" *
long overall "Overall impression"
list strengths "Strengths"
list issues "Issues to fix"
list line_edits "Line edits"

=== revision-checklist
name: Revision checklist
category: Writing and publishing
icon: spellcheck
description: Passes to make before a piece is done.
## Checklist
text piece "Piece" *
checklist structure "Structure" = "Clear opening|Logical order|Strong ending"
checklist clarity "Clarity" = "Cut filler|Short sentences|Defined jargon"
checklist polish "Polish" = "Spelling|Facts checked|Links work|Read aloud"

=== publication-plan
name: Publication plan
category: Writing and publishing
icon: publish
description: Where, when and how a piece will be published and promoted.
## Plan
text piece "Piece" *
text venue "Venue"
date publish_on "Publish date"
checklist steps "Steps" = "Final edit|Images|Metadata|Schedule|Promote"
list channels "Promotion channels"

=== content-calendar
name: Content calendar
category: Writing and publishing
icon: calendar_month
description: Plan content by date, channel and status.
## Calendar
text period "Period" *
table items "Content" [Date|Channel|Topic|Status]

=== headline-brainstorming
name: Headline brainstorming
category: Writing and publishing
icon: title
description: Generate many headline options and pick the best.
## Headlines
text piece "Piece" *
list options "Options"
text chosen "Chosen headline"
text why "Why"

=== personal-writing-style-guide
name: Personal writing style guide
category: Writing and publishing
icon: style
description: Your rules for voice, words and formatting.
## Style
long voice "Voice"
list do "Do"
list dont "Don't"
table words "Preferred words" [Use|Instead of]
list formatting "Formatting rules"

=== editorial-decision-log
name: Editorial decision log
category: Writing and publishing
icon: fact_check
description: Record editorial choices and why you made them.
## Log
text publication "Publication / project" *
table decisions "Decisions" [Date|Decision|Reason]
