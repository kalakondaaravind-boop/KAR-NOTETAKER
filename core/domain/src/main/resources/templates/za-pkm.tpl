#! AA. Personal knowledge management

=== permanent-note
name: Permanent note
category: Personal knowledge management
icon: push_pin
description: One idea in your own words, linked to related notes.
aliases: Evergreen note
## Idea
text idea "Idea (as a statement)" *
long explanation "Explanation in my own words"
list links "Related notes ([[Title]])"
text source "Source"

=== literature-note
name: Literature note
category: Personal knowledge management
icon: library_books
description: Notes on a source, in your own words, with references.
aliases: Source note
## Source
text source "Source" *
text locator "Page / timestamp"
long notes "Notes in my own words"
list quotes "Quotes (with page)"

=== fleeting-note
name: Fleeting note
category: Personal knowledge management
icon: flash_on
description: A quick thought to process later.
## Thought
long thought "Thought" *
select processed "Processed?" [No|Yes — became a permanent note|Discarded] = No

=== knowledge-map
name: Knowledge map
category: Personal knowledge management
icon: hub
description: A map of content (MOC) that links notes on a topic.
aliases: Cross-reference index, Personal wiki page
## Map
text topic "Topic" *
long overview "Overview"
list notes "Key notes ([[Title]])"
list questions "Open questions"

=== claim-and-evidence
name: Claim and evidence
category: Personal knowledge management
icon: balance
description: A claim with the evidence for and against it.
aliases: Contradictory evidence
## Claim
text claim "Claim" *
list for_ "Evidence for"
list against "Evidence against"
select confidence "My confidence" [High|Medium|Low] = Medium

=== research-backlog
name: Research backlog
category: Personal knowledge management
icon: pending_actions
description: Things to read, watch or investigate.
## Backlog
table items "Items" [Item|Type|Why|Priority]
date reviewed "Last reviewed" = today

=== personal-encyclopedia-entry
name: Personal encyclopedia entry
category: Personal knowledge management
icon: menu_book
description: A reference article on a subject, in your own words.
## Entry
text subject "Subject" *
text summary "One-line summary"
long body "Article"
list see_also "See also"
list sources "Sources"

=== knowledge-review
name: Knowledge review
category: Personal knowledge management
icon: rate_review
description: Periodically review and prune your notes.
## Review
date day "Date" = today
list revisited "Notes revisited"
list merged "Merged or deleted"
list new_links "New links made"
list gaps "Gaps noticed"

=== orphan-note-review
name: Orphan-note review
category: Personal knowledge management
icon: link_off
description: Connect or retire notes that have no links.
## Review
table orphans "Orphans" [Note|Action|Linked to]
date day "Date" = today
