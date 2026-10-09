#! G. Finance, accounting, and budgeting — organisational tools, not financial advice.

=== monthly-budget
name: Monthly budget
category: Finance, accounting, and budgeting
icon: account_balance_wallet
description: Plan income and spending by category for the month.
title: Budget — {{month}}
tags: finance
private: true
disclaimer: An organisational tool, not financial advice.
aliases: Household budget
## Income
currency income "Expected income"
## Spending
table categories "Categories" [Category|Budget|Actual|Difference] = "Housing;;;|Food;;;|Transport;;;|Utilities;;;|Savings;;;|Fun;;;"
## Review
long review "What to adjust next month"

=== expense-tracker
name: Expense tracker
category: Finance, accounting, and budgeting
icon: receipt_long
description: Log expenses as they happen.
private: true
aliases: Property expense tracker
## Expenses
text period "Period" * ? "e.g. June 2025"
table expenses "Expenses" [Date|Item|Category|Amount]
currency total "Total"

=== income-tracker
name: Income tracker
category: Finance, accounting, and budgeting
icon: payments
description: Record income by source and date.
private: true
## Income
text period "Period" *
table income "Income" [Date|Source|Amount|Notes]
currency total "Total"

=== savings-plan
name: Savings plan
category: Finance, accounting, and budgeting
icon: savings
description: A savings goal with a monthly contribution plan.
private: true
## Goal
text goal "Savings goal" *
currency target "Target amount"
currency saved "Saved so far"
date by "Target date"
currency monthly "Monthly contribution"
text account "Where it's kept"

=== debt-repayment-plan
name: Debt repayment plan
category: Finance, accounting, and budgeting
icon: credit_score
description: List debts and choose a repayment order.
private: true
disclaimer: An organisational tool, not financial advice.
## Debts
table debts "Debts" [Debt|Balance|Interest %|Minimum payment]
select method "Method" [Avalanche (highest interest first)|Snowball (smallest balance first)|Custom] = "Avalanche (highest interest first)"
currency extra "Extra monthly payment"
long plan "Plan"

=== net-worth-snapshot
name: Net worth snapshot
category: Finance, accounting, and budgeting
icon: account_balance
description: Assets minus liabilities at a point in time.
private: true
title: Net worth — {{date}}
## Snapshot
date as_of "As of" = today
table assets "Assets" [Asset|Value]
table liabilities "Liabilities" [Liability|Value]
currency net "Net worth"

=== financial-goals
name: Financial goals
category: Finance, accounting, and budgeting
icon: flag_circle
description: Short, medium and long-term money goals.
private: true
## Goals
table short "Short term (< 1 year)" [Goal|Amount|By]
table medium "Medium term (1–5 years)" [Goal|Amount|By]
table long_term "Long term" [Goal|Amount|By]

=== investment-research
name: Investment research
category: Finance, accounting, and budgeting
icon: query_stats
description: Research notes on an investment — facts, thesis and risks kept separate.
private: true
disclaimer: For personal research notes only; not investment advice.
## Investment
text name_field "Investment" *
url source "Main source"
long facts "Facts"
long thesis "My thesis"
list risks "Risks"
list questions "Open questions"

=== portfolio-review-notes
name: Portfolio review notes
category: Finance, accounting, and budgeting
icon: donut_small
description: Periodic review of allocation and decisions.
private: true
disclaimer: For personal notes only; not investment advice.
## Review
date as_of "As of" = today
table allocation "Allocation" [Holding|Weight %|Target %]
long changes "Changes considered"
list actions "Actions"

=== cash-flow-forecast
name: Cash flow forecast
category: Finance, accounting, and budgeting
icon: waterfall_chart
description: Expected inflows and outflows by period.
## Forecast
text scope "Scope" *
table periods "By period" [Period|Inflows|Outflows|Net|Closing balance]
list assumptions "Assumptions"

=== scenario-analysis
name: Scenario analysis
category: Finance, accounting, and budgeting
icon: device_hub
description: Compare base, upside and downside cases.
## Scenarios
text question "Question" *
table scenarios "Scenarios" [Scenario|Key assumptions|Result] = "Base;;|Upside;;|Downside;;"
long decision "What this means for the decision"

=== cost-estimate
name: Cost estimate
category: Finance, accounting, and budgeting
icon: request_quote
description: Itemised cost estimate with contingency.
aliases: Renovation estimate
## Estimate
text scope "What's being estimated" *
table items "Items" [Item|Quantity|Unit cost|Total]
number contingency "Contingency %" = 10 min=0 max=100
currency total "Total estimate"
list exclusions "Exclusions"

=== budget-variance-analysis
name: Budget variance analysis
category: Finance, accounting, and budgeting
icon: difference
description: Explain differences between budget and actuals.
## Variance
text period "Period" *
table lines "Lines" [Line|Budget|Actual|Variance|Explanation]
list actions "Actions"

=== pricing-analysis
name: Pricing analysis
category: Finance, accounting, and budgeting
icon: sell
description: Evaluate price points against cost, value and competitors.
## Pricing
text product "Product" *
currency cost "Unit cost"
table competitors "Competitor prices" [Competitor|Price|Notes]
long value "Customer value"
table options "Price options" [Price|Expected volume|Margin]
long recommendation "Recommendation"

=== unit-economics
name: Unit economics
category: Finance, accounting, and budgeting
icon: functions
description: Revenue and cost per unit or customer.
## Unit
text unit "Unit (customer / order / item)" *
currency revenue "Revenue per unit"
currency variable_cost "Variable cost per unit"
currency cac "Acquisition cost"
currency ltv "Lifetime value"
long notes "Notes and sources"

=== revenue-analysis
name: Revenue analysis
category: Finance, accounting, and budgeting
icon: bar_chart
description: Break revenue down by driver and explain changes.
## Revenue
text period "Period" *
table segments "By segment" [Segment|Revenue|Change %|Driver]
long story "What changed and why"

=== margin-analysis
name: Margin analysis
category: Finance, accounting, and budgeting
icon: stacked_line_chart
description: Gross and contribution margin by product or segment.
## Margins
text scope "Scope" *
table margins "Margins" [Product|Revenue|Cost|Margin %]
list levers "Improvement levers"

=== invoice-reference
name: Invoice reference
category: Finance, accounting, and budgeting
icon: receipt
description: Key details of an invoice for your records.
private: true
## Invoice
text vendor "Vendor / client" *
text number "Invoice number"
date issued "Issue date"
date due "Due date"
currency amount "Amount"
select status "Status" [Unpaid|Paid|Disputed] = Unpaid

=== receipt-reference
name: Receipt reference
category: Finance, accounting, and budgeting
icon: receipt_long
description: Record a purchase receipt (attach the photo to this note).
private: true
## Receipt
text merchant "Merchant" *
date purchased "Date" = today
currency amount "Amount"
text category "Category"
checkbox warranty "Has warranty"
date return_by "Return by"

=== tax-document-checklist
name: Tax document checklist
category: Finance, accounting, and budgeting
icon: folder_special
description: Gather documents for a tax return.
private: true
disclaimer: A checklist to organise documents; requirements vary by jurisdiction. Consult a qualified adviser.
## Tax year
text year "Tax year" *
checklist income_docs "Income documents"
checklist deductions "Deductions and receipts"
checklist other "Other documents"
date deadline "Filing deadline"

=== audit-preparation
name: Audit preparation
category: Finance, accounting, and budgeting
icon: policy
description: Prepare evidence and owners for an audit.
## Audit
text audit "Audit" *
date fieldwork "Fieldwork date"
table requests "Requests" [Request|Owner|Evidence|Status]
list risks "Known issues"

=== financial-close-checklist
name: Financial close checklist
category: Finance, accounting, and budgeting
icon: event_available
description: Month-end close steps.
## Close
text period "Period" *
checklist steps "Steps" = "Bank reconciliations|Accruals|Prepayments|Revenue recognition|Fixed assets|Intercompany|Review P&L|Review balance sheet"
long issues "Issues"

=== reconciliation-checklist
name: Reconciliation checklist
category: Finance, accounting, and budgeting
icon: sync_alt
description: Reconcile an account and explain differences.
## Reconciliation
text account "Account" *
date as_of "As of" = today
currency ledger "Ledger balance"
currency statement "Statement balance"
table items "Reconciling items" [Item|Amount|Explanation]

=== personal-financial-review
name: Personal financial review
category: Finance, accounting, and budgeting
icon: assessment
description: A periodic look at income, spending, saving and goals.
private: true
## Review
text period "Period" *
currency income "Income"
currency spending "Spending"
currency saved "Saved"
long noticed "What I noticed"
checklist actions "Actions"
