#! V. Operations, supply chain, and manufacturing

=== standard-operating-procedure
name: Standard operating procedure
category: Operations, supply chain, and manufacturing
icon: rule
description: A repeatable procedure with purpose, steps and checks.
aliases: Work instruction
## SOP
text procedure "Procedure" *
text purpose "Purpose"
text scope "Scope"
list prerequisites "Prerequisites"
list steps "Steps"
list checks "Quality checks"
text version "Version"

=== process-map-notes
name: Process map notes
category: Operations, supply chain, and manufacturing
icon: schema
description: Steps, owners, inputs and outputs of a process.
## Process
text process "Process" *
table steps "Steps" [Step|Owner|Input|Output|Time]
list pain_points "Pain points"

=== procurement-checklist
name: Procurement checklist
category: Operations, supply chain, and manufacturing
icon: shopping_bag
description: Steps to buy something properly.
## Procurement
text item "Item / service" *
checklist steps "Steps" = "Requirements defined|Quotes obtained|Approval received|PO raised|Delivery confirmed|Invoice matched"
currency budget "Budget"

=== quality-inspection
name: Quality inspection
category: Operations, supply chain, and manufacturing
icon: verified
description: Inspect a batch against acceptance criteria.
## Inspection
text batch "Batch / item" *
date day "Date" = today
table checks "Checks" [Characteristic|Spec|Measured|Pass?]
select result "Result" [Accept|Reject|Rework] = Accept

=== nonconformance-report
name: Nonconformance report
category: Operations, supply chain, and manufacturing
icon: report
description: Document a nonconformance and its disposition.
## NCR
text item "Item" *
long description "Description"
text detected "Where detected"
select disposition "Disposition" [Use as is|Rework|Scrap|Return to supplier] = Rework
text root_cause "Root cause"

=== corrective-action
name: Corrective action
category: Operations, supply chain, and manufacturing
icon: build_circle
description: Fix the cause of a problem and verify it worked.
## CAPA
text problem "Problem" *
text root_cause "Root cause"
list actions "Actions"
date due "Due"
text verification "Effectiveness check"

=== preventive-action
name: Preventive action
category: Operations, supply chain, and manufacturing
icon: shield
description: Prevent a potential problem before it occurs.
## Preventive action
text risk "Potential problem" *
list actions "Actions"
date due "Due"
text verification "How we'll know it worked"

=== maintenance-log
name: Maintenance log
category: Operations, supply chain, and manufacturing
icon: engineering
description: Planned and unplanned maintenance on equipment.
## Log
text equipment "Equipment" *
table entries "Entries" [Date|Work done|By|Downtime]

=== process-improvement
name: Process improvement
category: Operations, supply chain, and manufacturing
icon: auto_graph
description: PDCA: plan, do, check, act.
## PDCA
text process "Process" *
long plan "Plan"
long do "Do"
long check "Check"
long act "Act"

=== lean-waste-observation
name: Lean waste observation
category: Operations, supply chain, and manufacturing
icon: delete_sweep
description: Spot the eight wastes in a process.
## Observation
text area "Area" *
table wastes "Wastes" [Waste type|Observed|Idea] = "Transport;;|Inventory;;|Motion;;|Waiting;;|Overproduction;;|Overprocessing;;|Defects;;|Unused talent;;"

=== value-stream-analysis
name: Value stream analysis
category: Operations, supply chain, and manufacturing
icon: water
description: Value-adding versus waiting time across a value stream.
## Value stream
text product "Product / service" *
table steps "Steps" [Step|Process time|Wait time|Value-adding?]
long future "Future-state ideas"

=== capacity-planning
name: Capacity planning
category: Operations, supply chain, and manufacturing
icon: stacked_bar_chart
description: Demand versus capacity and how to close the gap.
## Capacity
text resource "Resource" *
table periods "By period" [Period|Demand|Capacity|Gap]
list options "Options"

=== warehouse-checklist
name: Warehouse checklist
category: Operations, supply chain, and manufacturing
icon: warehouse
description: Daily warehouse safety and housekeeping checks.
## Checklist
text site "Site" *
checklist checks "Checks" = "Aisles clear|Racking undamaged|Fire exits clear|PPE available|Stock counts reconciled"

=== shipment-exception-record
name: Shipment exception record
category: Operations, supply chain, and manufacturing
icon: local_shipping
description: A late, damaged or missing shipment and its resolution.
## Exception
text shipment "Shipment / reference" *
select type "Type" [Late|Damaged|Missing|Wrong item] = Late
long description "Description"
text resolution "Resolution"
