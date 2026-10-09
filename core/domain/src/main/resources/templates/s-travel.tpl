#! S. Travel and outdoor activities — keep sensitive document numbers out of shared exports.

=== trip-overview
name: Trip overview
category: Travel and outdoor activities
icon: flight
description: One page with dates, bookings, budget and key contacts for a trip.
title: Trip — {{field:destination}}
tags: travel
## Trip
text destination "Destination" *
date depart "Depart"
date return_date "Return"
list travellers "Travellers"
table bookings "Bookings" [Type|Details|Reference]
currency budget "Budget"
list contacts "Key contacts"

=== itinerary
name: Itinerary
category: Travel and outdoor activities
icon: map
description: Day-by-day plan for a trip.
## Day +repeat
date day "Date" *
text place "Base"
table plan "Plan" [Time|Activity|Notes]

=== travel-budget
name: Travel budget
category: Travel and outdoor activities
icon: wallet
description: Planned versus actual trip spending.
## Budget
text trip "Trip" *
table lines "Budget" [Category|Planned|Actual] = "Transport;;|Accommodation;;|Food;;|Activities;;|Other;;"
currency total "Total"

=== reservation-reference
name: Reservation reference
category: Travel and outdoor activities
icon: confirmation_number
description: Booking details in one place.
private: true
aliases: Accommodation details
## Reservation
text what "Reservation" *
text provider "Provider"
text reference "Confirmation code"
datetime check_in "Start / check-in"
datetime check_out "End / check-out"
text address "Address"
text cancellation "Cancellation policy"

=== flight-details
name: Flight details
category: Travel and outdoor activities
icon: flight_takeoff
description: Flight numbers, times, seats and baggage.
private: true
## Flight +repeat
text flight "Flight number" *
datetime departs "Departs"
datetime arrives "Arrives"
text route "From → to"
text seat "Seat"
text baggage "Baggage allowance"

=== destination-research
name: Destination research
category: Travel and outdoor activities
icon: travel_explore
description: What to know before you go.
## Destination
text destination "Destination" *
text best_time "Best time to go"
list customs "Customs and etiquette"
list safety "Safety notes"
list transport "Getting around"
list must_see "Must see"

=== places-to-visit
name: Places to visit
category: Travel and outdoor activities
icon: place
description: Places with area, opening times and priority.
## Places
text destination "Destination" *
table places "Places" [Place|Area|Hours|Priority]

=== restaurant-research
name: Restaurant research
category: Travel and outdoor activities
icon: restaurant
description: Restaurants to try with notes and bookings.
## Restaurants
text city "City" *
table places "Restaurants" [Name|Cuisine|Price|Book?|Notes]

=== local-transportation-notes
name: Local transportation notes
category: Travel and outdoor activities
icon: directions_bus
description: How to get around: passes, apps and routes.
## Transport
text city "City" *
list options "Options"
text passes "Passes / tickets"
list routes "Useful routes"

=== emergency-information
name: Emergency information
category: Travel and outdoor activities
icon: emergency
description: Who to call and where to go if something goes wrong.
private: true
aliases: Emergency preparedness
## Emergency
text location "Location" *
text emergency_number "Local emergency number"
text embassy "Embassy / consulate"
text insurance "Insurance contact"
list contacts "Emergency contacts"
text hospital "Nearest hospital"

=== travel-document-checklist
name: Travel document checklist
category: Travel and outdoor activities
icon: badge
description: Documents to check before travelling (store numbers elsewhere).
private: true
## Documents
text trip "Trip" *
checklist docs "Documents" = "Passport valid 6+ months|Visa|Insurance|Tickets|Driving licence|Vaccination records"
date passport_expiry "Passport expiry"

=== hiking-plan
name: Hiking plan
category: Travel and outdoor activities
icon: hiking
description: Route, timing, kit and safety plan for a hike.
## Hike
text route "Route" *
date day "Date"
number distance "Distance (km)" min=0
text elevation "Elevation gain"
time start "Start time"
time turnaround "Turnaround time"
checklist kit "Kit" = "Water|Food|Map / GPS|Layers|First aid|Head torch"
text told_someone "Who knows my plan"

=== camping-checklist
name: Camping checklist
category: Travel and outdoor activities
icon: camping
description: Gear and food for a camping trip.
## Camping
text trip "Trip" *
checklist shelter "Shelter and sleep" = "Tent|Sleeping bag|Sleeping mat"
checklist kitchen "Kitchen" = "Stove|Fuel|Pot|Utensils|Water filter"
checklist clothing "Clothing"
checklist other "Other"

=== road-trip-planner
name: Road trip planner
category: Travel and outdoor activities
icon: directions_car
description: Legs, stops and vehicle checks for a road trip.
## Road trip
text trip "Trip" *
table legs "Legs" [Day|From|To|Distance|Stops]
checklist vehicle "Vehicle checks" = "Tyres|Oil|Spare|Documents"

=== post-trip-review
name: Post-trip review
category: Travel and outdoor activities
icon: rate_review
description: What to remember and change for next time.
## Review
text trip "Trip" *
list highlights "Highlights"
list would_skip "Would skip"
list next_time "Next time"
currency spent "Total spent"
