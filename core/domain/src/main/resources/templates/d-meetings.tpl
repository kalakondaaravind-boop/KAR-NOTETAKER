#! D. Meetings and conversations — private records only; no participant accounts or sharing.

=== meeting-agenda
name: Meeting agenda
category: Meetings and conversations
icon: event
description: Prepare an agenda with goals, topics and time boxes.
title: Agenda — {{field:meeting}}
## Meeting
text meeting "Meeting" *
datetime when "When" = now
text goal "Goal of the meeting"
table topics "Topics" [Topic|Minutes|Desired outcome]
list prep "Pre-reads / preparation"

=== meeting-notes
name: Meeting notes
category: Meetings and conversations
icon: groups
description: Notes, decisions and actions from a meeting.
title: {{field:meeting}} — {{date}}
tags: meeting
aliases: Meeting record, Governance meeting notes
## Meeting
text meeting "Meeting" *
date day "Date" = today
list attendees "Attendees"
## Notes
long notes "Discussion"
list decisions "Decisions"
checklist actions "Action items"

=== one-to-one-meeting
name: One-to-one meeting
category: Meetings and conversations
icon: people
description: A 1:1 conversation record: updates, topics, feedback and follow-ups.
title: 1:1 with {{field:person}} — {{date}}
## 1:1
text person "With" *
date day "Date" = today
list their_topics "Their topics"
list my_topics "My topics"
long feedback "Feedback exchanged"
checklist follow_ups "Follow-ups"

=== team-meeting-notes
name: Team meeting notes for personal reference only
category: Meetings and conversations
icon: diversity_3
description: Your own notes from a team meeting — kept privately, not shared.
aliases: Team meeting notes
## Meeting
text team "Team / meeting" *
date day "Date" = today
list updates "Updates I noted"
list relevant "What's relevant to me"
checklist my_actions "My action items"

=== interview-notes
name: Interview notes
category: Meetings and conversations
icon: record_voice_over
description: Structured notes from an interview, separating observations from impressions.
## Interview
text who "Interviewee" *
date day "Date" = today
text purpose "Purpose"
table qa "Questions and answers" [Question|Answer]
long observations "Observations (what was said/done)"
long impressions "My interpretation"

=== customer-interview
name: Customer interview
category: Meetings and conversations
icon: support_agent
description: Learn about a customer's context, problems and current solutions.
## Customer
text customer "Customer / segment" *
date day "Date" = today
## Findings
long context "Their context"
list problems "Problems mentioned"
long current "Current solution and workarounds"
list quotes "Notable quotes"
list insights "Insights"

=== user-research-interview
name: User research interview
category: Meetings and conversations
icon: person_search
description: A research session record linking tasks, behaviour and insights.
aliases: Problem interview
## Session
text participant "Participant (pseudonym)" *
text segment "Segment"
date day "Date" = today
## Notes
table observations "Observations" [Task / question|What they did or said|Interpretation]
list pain_points "Pain points"
list opportunities "Opportunities"

=== discovery-call
name: Discovery call
category: Meetings and conversations
icon: call
description: Understand needs, goals and fit on a first call.
aliases: Sales discovery
## Call
text company "Company / person" *
date day "Date" = today
long situation "Current situation"
list goals "Goals"
list challenges "Challenges"
text budget "Budget / timeline"
text next_step "Agreed next step"

=== sales-call
name: Sales call
category: Meetings and conversations
icon: phone_in_talk
description: Notes from a sales conversation: needs, objections and next steps.
## Call
text account "Account" *
date day "Date" = today
select stage "Stage" [Prospect|Qualified|Proposal|Negotiation|Closed won|Closed lost] = Prospect
long needs "Needs discussed"
list objections "Objections"
text next_step "Next step"
date follow_up "Follow up on"

=== client-meeting
name: Client meeting
category: Meetings and conversations
icon: business_center
description: Client meeting record with requests, commitments and risks.
## Meeting
text client "Client" *
date day "Date" = today
list requests "Client requests"
list commitments "Our commitments"
list risks "Risks / concerns"
checklist actions "Actions"

=== workshop-notes
name: Workshop notes
category: Meetings and conversations
icon: co_present
description: Capture exercises, outputs and decisions from a workshop.
## Workshop
text workshop "Workshop" *
date day "Date" = today
## Exercise +repeat
text exercise "Exercise" *
long output "Output"
## Wrap-up
list decisions "Decisions"
checklist actions "Actions"

=== brainstorming-session
name: Brainstorming session
category: Meetings and conversations
icon: tips_and_updates
description: Generate many ideas, then cluster and choose.
aliases: Brainstorm
## Prompt
text prompt "Problem / prompt" *
## Ideas
list ideas "Ideas (quantity over quality)"
## Converge
table clusters "Clusters" [Theme|Ideas]
list shortlist "Shortlist"

=== retrospective
name: Retrospective
category: Meetings and conversations
icon: replay
description: What went well, what didn't, what to try next.
## Retro
text scope "Sprint / period / event" *
list well "What went well"
list not_well "What didn't go well"
list try "What to try next"
checklist actions "Actions"

=== stakeholder-conversation
name: Stakeholder conversation
category: Meetings and conversations
icon: handshake
description: Record a stakeholder's interests, concerns and influence.
## Conversation
text stakeholder "Stakeholder" *
date day "Date" = today
long interests "Interests"
long concerns "Concerns"
select stance "Stance" [Supportive|Neutral|Sceptical|Opposed] = Neutral
text next "Next touchpoint"

=== phone-call-record
name: Phone call record
category: Meetings and conversations
icon: call_log
description: Who called, what was said and what happens next.
## Call
text who "Who" *
datetime when "When" = now
select direction "Direction" [Incoming|Outgoing] = Outgoing
text reference "Reference / case number"
long summary "Summary"
checklist next "Next steps"

=== follow-up-email-preparation
name: Follow-up email preparation
category: Meetings and conversations
icon: mail
description: Draft the key points of a follow-up email before writing it.
## Email
text to "To" *
text subject "Subject"
list recap "Recap of what was discussed"
list asks "What I'm asking for"
date deadline "Deadline to mention"
long draft "Draft"

=== conference-session-notes
name: Conference session notes
category: Meetings and conversations
icon: mic
description: Notes from a talk: key ideas, quotes, and what to do with them.
aliases: Session summary
## Session
text talk "Talk" *
text speaker "Speaker"
text event "Event"
list key_ideas "Key ideas"
list quotes "Quotes"
checklist to_try "To try / follow up"

=== training-session-notes
name: Training session notes
category: Meetings and conversations
icon: model_training
description: What a training covered and how you'll apply it.
## Training
text course "Training" *
date day "Date" = today
list covered "Topics covered"
long apply "How I'll apply it"
list questions "Open questions"
rating usefulness "Usefulness" max=5
