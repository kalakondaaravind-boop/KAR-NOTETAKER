#! T. Home, family, and personal administration

=== home-inventory
name: Home inventory
category: Home, family, and personal administration
icon: chair
description: Belongings by room with value, for insurance.
private: true
aliases: Collectibles inventory
## Inventory
text property "Property / collection" *
table items "Items" [Item|Room / location|Value|Purchased|Serial]

=== home-maintenance
name: Home maintenance
category: Home, family, and personal administration
icon: home_repair_service
description: Seasonal maintenance tasks and records.
## Maintenance
checklist spring "Spring"
checklist summer "Summer"
checklist autumn "Autumn"
checklist winter "Winter"
table done "Completed" [Date|Task|Cost]

=== cleaning-schedule
name: Cleaning schedule
category: Home, family, and personal administration
icon: cleaning_services
description: Who cleans what, and how often.
## Schedule
checklist daily "Daily"
checklist weekly "Weekly"
checklist monthly "Monthly"

=== repair-record
name: Repair record
category: Home, family, and personal administration
icon: handyman
description: Record of a repair: problem, who fixed it, cost and warranty.
aliases: Maintenance record
## Repair
text item "Item / area" *
date day "Date" = today
long problem "Problem"
text by "Repaired by"
currency cost "Cost"
date warranty_until "Warranty until"

=== warranty-register
name: Warranty register
category: Home, family, and personal administration
icon: verified
description: Warranties and where the paperwork is.
## Warranties
table items "Items" [Item|Purchased|Warranty until|Receipt location]
date reviewed "Last reviewed" = today

=== appliance-information
name: Appliance information
category: Home, family, and personal administration
icon: kitchen
description: Model, manuals and service info for an appliance.
aliases: Equipment record
## Appliance
text appliance "Appliance" *
text model "Model / serial"
date purchased "Purchased"
url manual "Manual link"
text service "Service contact"
long notes "Notes"

=== renovation-plan
name: Renovation plan
category: Home, family, and personal administration
icon: format_paint
description: Scope, budget, trades and timeline for a renovation.
## Renovation
text project "Project" *
long scope "Scope"
currency budget "Budget"
table trades "Trades" [Trade|Contact|Quote|Booked]
table timeline "Timeline" [Phase|Start|End]

=== moving-checklist
name: Moving checklist
category: Home, family, and personal administration
icon: local_shipping
description: Everything to do before and after a move.
## Move
date move_day "Move date" *
checklist before "Before" = "Book movers|Change address|Transfer utilities|Pack room by room"
checklist after "After" = "Unpack essentials|Register locally|Update documents"

=== pet-care-notes
name: Pet care notes
category: Home, family, and personal administration
icon: pets
description: Care instructions, vet details and routines.
## Pet
text pet "Pet" *
text vet "Vet"
list routine "Daily routine"
list food "Food"
table health "Health record" [Date|Event]

=== family-event-plan
name: Family event plan
category: Home, family, and personal administration
icon: celebration
description: Plan a family event.
aliases: Event plan
## Event
text event "Event" *
datetime when "When"
text venue "Venue"
list guests "Guests"
checklist tasks "Tasks"
currency budget "Budget"

=== gift-ideas
name: Gift ideas
category: Home, family, and personal administration
icon: redeem
description: Gift ideas per person throughout the year.
## Person +repeat
text person "Person" *
date occasion "Next occasion"
list ideas "Ideas"
text given "Given before"

=== gardening-log
name: Gardening log
category: Home, family, and personal administration
icon: yard
description: What was planted, when, and how it did.
## Log
text garden "Garden / bed" *
table plantings "Plantings" [Plant|Sown|Location|Result]
long notes "Season notes"

=== plant-care
name: Plant care
category: Home, family, and personal administration
icon: potted_plant
description: Care needs for a house plant.
## Plant
text plant "Plant" *
select light "Light" [Low|Medium|Bright indirect|Direct] = Medium
text water "Watering"
text feed "Feeding"
date repotted "Last repotted"

=== personal-document-index
name: Personal document index
category: Home, family, and personal administration
icon: folder
description: Where your important documents are kept (not the documents themselves).
private: true
aliases: Property document index
## Documents
table docs "Documents" [Document|Location|Expiry|Notes]
date reviewed "Last reviewed" = today

=== subscription-register
name: Subscription register
category: Home, family, and personal administration
icon: subscriptions
description: Recurring subscriptions, costs and renewal dates.
aliases: Renewal reminders
## Subscriptions
table subs "Subscriptions" [Service|Cost|Billing|Renews|Keep?]
currency monthly_total "Monthly total"

=== household-reference-guide
name: Household reference guide
category: Home, family, and personal administration
icon: home
description: How the house works, for you or a house-sitter.
## Guide
list utilities "Utilities and shut-offs"
list wifi "Wi-Fi and devices (no passwords)"
list bins "Bin days"
list contacts "Useful contacts"
long quirks "Quirks"
