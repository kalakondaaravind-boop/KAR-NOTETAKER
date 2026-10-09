#! Z. Events and conferences

=== conference-schedule
name: Conference schedule
category: Events and conferences
icon: event_note
description: Sessions you plan to attend and backups.
## Conference
text conference "Conference" *
table sessions "Sessions" [Time|Session|Room|Backup]
list people "People to meet"

=== speaker-notes
name: Speaker notes
category: Events and conferences
icon: mic_external_on
description: Notes for delivering a talk.
## Talk
text talk "Talk" *
number minutes "Length (min)" min=1
long opening "Opening"
list sections "Sections"
long close "Close"
list props "Demos / props"

=== networking-preparation
name: Networking preparation
category: Events and conferences
icon: connect_without_contact
description: Who to meet, what to ask and your intro.
## Prep
text event "Event" *
text intro "My 30-second intro"
table targets "People to meet" [Person|Why|Question]

=== event-checklist
name: Event checklist
category: Events and conferences
icon: checklist
description: Logistics checklist for running an event.
## Checklist
text event "Event" *
checklist before "Before" = "Venue booked|Invites sent|Catering|AV tested|Signage"
checklist day "On the day"
checklist after "After" = "Thank-yous|Feedback survey|Invoices paid"

=== event-budget
name: Event budget
category: Events and conferences
icon: payments
description: Planned versus actual event costs.
## Budget
text event "Event" *
table lines "Lines" [Item|Planned|Actual]
currency total "Total"

=== vendor-checklist
name: Vendor checklist
category: Events and conferences
icon: store
description: Vendors booked for an event and their status.
## Vendors
text event "Event" *
table vendors "Vendors" [Vendor|Service|Contact|Deposit paid?|Confirmed?]

=== post-event-retrospective
name: Post-event retrospective
category: Events and conferences
icon: replay
description: Review how an event went.
## Retro
text event "Event" *
number attendees "Attendees" min=0
list well "What went well"
list improve "What to improve"
rating overall "Overall" max=5
