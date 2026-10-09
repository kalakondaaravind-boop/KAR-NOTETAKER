#! J. Reading, books, media, and entertainment

=== book-notes
name: Book notes
category: Reading, books, media, and entertainment
icon: menu_book
description: Capture a book's key ideas, quotes and how you'll use them.
title: {{field:book}}
tags: books
aliases: Reading highlights, Reading notes
## Book
text book "Title" *
text author "Author"
date finished "Finished"
## Notes
long summary "Summary in three sentences"
list ideas "Key ideas"
list quotes "Quotes"
long apply "How I'll apply it"

=== book-review
name: Book review
category: Reading, books, media, and entertainment
icon: reviews
description: Your verdict on a book and who it's for.
## Review
text book "Title" *
text author "Author"
rating rating "Rating" max=5
long liked "What I liked"
long disliked "What I didn't"
text for_whom "Who should read it"

=== reading-list
name: Reading list
category: Reading, books, media, and entertainment
icon: list
description: Books to read, reading and read.
## Lists
table to_read "To read" [Title|Author|Why]
list reading "Reading now"
table read "Read" [Title|Finished|Rating]

=== reading-progress
name: Reading progress
category: Reading, books, media, and entertainment
icon: auto_stories
description: Track progress through a long book.
## Progress
text book "Title" *
number total_pages "Total pages" min=1
table sessions "Sessions" [Date|From page|To page|Note]
progress progress "Progress"

=== article-summary
name: Article summary
category: Reading, books, media, and entertainment
icon: newspaper
description: Summarise an article with its argument and your response.
## Article
text article "Title" *
url link "Link"
text author "Author / outlet"
long argument "Main argument"
list evidence "Evidence used"
long response "My response"

=== podcast-notes
name: Podcast notes
category: Reading, books, media, and entertainment
icon: podcasts
description: Notes from a podcast episode.
## Episode
text show "Show" *
text episode "Episode"
list ideas "Key ideas"
list timestamps "Timestamps worth revisiting"
list follow_up "Follow-ups"

=== video-notes
name: Video notes
category: Reading, books, media, and entertainment
icon: smart_display
description: Notes from a video or lecture recording.
aliases: Course video notes
## Video
text video "Video" *
url link "Link"
table points "Points" [Timestamp|Note]
long summary "Summary"

=== film-review
name: Film review
category: Reading, books, media, and entertainment
icon: movie
description: Your thoughts on a film.
## Film
text film "Film" *
text year "Year"
rating rating "Rating" max=10
long thoughts "Thoughts"
list scenes "Memorable scenes"

=== tv-series-notes
name: TV series notes
category: Reading, books, media, and entertainment
icon: tv
description: Track a series: where you are and what you thought.
## Series
text series "Series" *
text platform "Where to watch"
table episodes "Episodes" [Season/Ep|Watched|Note]
rating rating "Rating" max=10

=== music-discovery
name: Music discovery
category: Reading, books, media, and entertainment
icon: library_music
description: Artists, albums and tracks to explore or remember.
aliases: Listening list
## Music
table to_listen "To listen to" [Artist|Album / track|Recommended by]
table liked "Liked" [Artist|Album / track|Why]

=== quote-collection
name: Quote collection
category: Reading, books, media, and entertainment
icon: format_quote
description: Quotes with source and why they resonate.
## Quote +repeat
long quote "Quote" *
text source "Source"
text why "Why it resonates"

=== author-profile
name: Author profile
category: Reading, books, media, and entertainment
icon: person_book
description: Notes about an author and their works.
## Author
text author "Author" *
long background "Background"
table works "Works" [Title|Year|Read?]
list themes "Recurring themes"

=== media-recommendation
name: Media recommendation
category: Reading, books, media, and entertainment
icon: thumb_up
description: Record a recommendation and who gave it.
## Recommendation
text title_line "Title" *
select kind "Type" [Book|Film|Series|Podcast|Music|Game|Article|Other] = Book
text from "Recommended by"
long why "Why they recommended it"

=== watchlist
name: Watchlist
category: Reading, books, media, and entertainment
icon: playlist_play
description: Films and shows to watch.
## Watchlist
table films "Films" [Title|Where|Priority]
table shows "Series" [Title|Where|Priority]

=== knowledge-extracted-from-media
name: Knowledge extracted from media
category: Reading, books, media, and entertainment
icon: lightbulb
description: Pull durable, reusable ideas out of something you consumed.
## Source
text source "Source" *
## Idea +repeat
text idea "Idea" *
long in_my_words "In my own words"
text connects_to "Connects to"
