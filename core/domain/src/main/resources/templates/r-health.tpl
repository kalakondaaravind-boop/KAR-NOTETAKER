#! R. Health, fitness, and well-being — personal tracking only; never diagnosis or treatment.

=== fitness-goal
name: Fitness goal
category: Health, fitness, and well-being
icon: fitness_center
description: A fitness goal with a baseline and plan.
private: true
## Goal
text goal "Goal" *
text baseline "Starting point"
date by "Target date"
list plan "Plan"
progress progress "Progress"

=== workout-log
name: Workout log
category: Health, fitness, and well-being
icon: sports_gymnastics
description: Exercises, sets and how it felt.
private: true
title: Workout — {{date}}
## Workout
date day "Date" = today
table exercises "Exercises" [Exercise|Sets|Reps|Weight]
number minutes "Duration (min)" min=0
rating effort "Effort" max=10
long notes "Notes"

=== exercise-progress
name: Exercise progress
category: Health, fitness, and well-being
icon: show_chart
description: Track one exercise's progress over time.
private: true
## Exercise
text exercise "Exercise" *
table history "History" [Date|Best set|Notes]

=== running-log
name: Running log
category: Health, fitness, and well-being
icon: directions_run
description: Distance, time and how the run felt.
private: true
title: Run — {{date}}
## Run
date day "Date" = today
number distance "Distance (km)" min=0
text time "Time"
select type "Type" [Easy|Tempo|Intervals|Long|Race] = Easy
rating feel "Feel" max=5
long notes "Notes"

=== meal-planning
name: Meal planning
category: Health, fitness, and well-being
icon: restaurant_menu
description: Plan meals for the week and build a shopping list.
aliases: Meal plan
## Week
table meals "Meals" [Day|Breakfast|Lunch|Dinner]
checklist shopping "Shopping list"

=== recipe-notes
name: Recipe notes
category: Health, fitness, and well-being
icon: soup_kitchen
description: A recipe with ingredients, method and your tweaks.
aliases: Recipe collection
## Recipe
text recipe "Recipe" *
number servings "Servings" min=1
list ingredients "Ingredients"
list method "Method"
long tweaks "My tweaks"
url source "Source"

=== sleep-journal
name: Sleep journal
category: Health, fitness, and well-being
icon: bedtime
description: Bedtime, wake time and quality to spot patterns.
private: true
disclaimer: Personal tracking only, not medical-grade monitoring.
title: Sleep — {{date}}
## Night
time bed "Bedtime"
time wake "Wake time"
rating quality "Quality" max=5
multiselect factors "Factors" [Caffeine|Alcohol|Screen time|Exercise|Stress|Late meal]
long notes "Notes"

=== wellness-reflection
name: Wellness reflection
category: Health, fitness, and well-being
icon: spa
description: Check in across body, mind and connection.
private: true
disclaimer: A personal reflection, not a diagnostic tool.
## Check-in
rating body "Body" max=5
rating mind "Mind" max=5
rating connection "Connection" max=5
long notes "What I notice"
text one_thing "One kind thing for myself"

=== appointment-preparation
name: Appointment preparation
category: Health, fitness, and well-being
icon: medical_services
description: Prepare for a healthcare appointment so nothing is forgotten.
private: true
disclaimer: For organising your own notes. Follow your healthcare professional's advice.
## Appointment
text with "With" *
datetime when "When"
long reason "Reason for visit"
list changes "Changes since last visit"
list questions "Questions"
long outcome "What was said / next steps"

=== questions-for-healthcare-professional
name: Questions for a healthcare professional
category: Health, fitness, and well-being
icon: help
description: Questions to ask and the answers you received.
private: true
disclaimer: Record answers from your healthcare professional; this app does not give medical advice.
## Questions +repeat
text question "Question" *
long answer "Answer received"

=== symptom-diary
name: General symptom diary
category: Health, fitness, and well-being
icon: monitor_heart
description: Log symptoms with timing and context to share with a professional.
private: true
disclaimer: A personal record to support conversations with a professional; not a diagnostic tool.
## Entry +repeat
datetime when "When" = now
text symptom "Symptom" *
rating severity "Severity" max=10
text context "Context (food, activity, etc.)"

=== personal-health-record-index
name: Personal health record index
category: Health, fitness, and well-being
icon: folder_shared
description: Where your health records, providers and key dates are.
private: true
## Index
table providers "Providers" [Provider|Specialty|Contact]
table records "Records" [Record|Date|Where kept]
list emergency "Emergency information"

=== medication-information-record
name: Medication information record
category: Health, fitness, and well-being
icon: medication
description: Medications as prescribed, for your reference.
private: true
disclaimer: Record only what your prescriber has told you. This app does not give dosing advice.
## Medications
table meds "Medications" [Medication|As prescribed|Prescriber|Start date]
list allergies "Allergies"

=== rehabilitation-exercise-notes
name: Rehabilitation exercise notes as prescribed by a professional
category: Health, fitness, and well-being
icon: accessibility
description: Exercises exactly as your physiotherapist or clinician prescribed them.
private: true
disclaimer: Follow your clinician's instructions; stop and seek advice if something hurts.
aliases: Rehabilitation exercise notes
## Programme
text prescribed_by "Prescribed by" *
table exercises "Exercises" [Exercise|As prescribed|Notes]
table log "Log" [Date|Done?|How it felt]
