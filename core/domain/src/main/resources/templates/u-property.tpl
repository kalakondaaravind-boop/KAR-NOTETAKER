#! U. Real estate and property

=== property-comparison
name: Property comparison
category: Real estate and property
icon: holiday_village
description: Compare properties side by side on what matters to you.
## Properties
text search "Search" *
table props "Properties" [Address|Price|Size|Bedrooms|Commute|Score]
long notes "Notes"

=== property-viewing-checklist
name: Property viewing checklist
category: Real estate and property
icon: real_estate_agent
description: What to check at a viewing.
## Viewing
text address "Address" *
datetime when "When"
checklist checks "Checks" = "Damp or mould|Water pressure|Windows and doors|Heating|Noise|Storage|Natural light|Phone signal"
list questions "Questions for the agent"
rating impression "Impression" max=5

=== rental-property-notes
name: Rental property notes
category: Real estate and property
icon: apartment
description: Key facts about a rental property you own or rent.
## Property
text address "Address" *
text landlord_agent "Landlord / agent"
currency rent "Rent"
date lease_end "Lease end"
long notes "Notes"

=== tenant-reference-notes
name: Tenant reference notes
category: Real estate and property
icon: badge
description: Private notes on tenancy details for your own reference.
private: true
## Tenancy
text property "Property" *
text tenant "Tenant"
date start "Start"
currency deposit "Deposit"
text deposit_scheme "Deposit protection"
long notes "Notes"

=== inspection-notes
name: Inspection notes
category: Real estate and property
icon: search
description: Condition by room with photos attached to the note.
## Inspection
text property "Property" *
date day "Date" = today
table rooms "Rooms" [Room|Condition|Issues]
list actions "Actions"

=== mortgage-comparison
name: Mortgage comparison
category: Real estate and property
icon: account_balance
description: Compare mortgage offers.
disclaimer: An organisational tool, not financial advice.
## Offers
currency loan "Loan amount"
table offers "Offers" [Lender|Rate|Fees|Term|Monthly payment]
long notes "Notes"

=== property-investment-assumptions
name: Property investment assumptions
category: Real estate and property
icon: calculate
description: The assumptions behind a property investment.
disclaimer: An organisational tool, not financial advice.
## Assumptions
text property "Property" *
currency price "Price"
currency rent "Expected rent"
number vacancy "Vacancy %" min=0 max=100
currency costs "Annual costs"
number growth "Growth % (assumed)"
long risks "Risks"

=== real-estate-due-diligence
name: Real estate due diligence
category: Real estate and property
icon: fact_check
description: Checks before buying a property.
disclaimer: Requirements vary by jurisdiction; consult professionals.
## Due diligence
text property "Property" *
checklist checks "Checks" = "Title|Survey|Planning permissions|Searches|Service charges|Flood risk"
list issues "Issues found"

=== closing-checklist
name: Closing checklist
category: Real estate and property
icon: key
description: Steps to complete a purchase or sale.
## Closing
text property "Property" *
date completion "Completion date"
checklist steps "Steps" = "Funds ready|Contracts signed|Insurance from completion|Utilities transferred|Keys collected"
