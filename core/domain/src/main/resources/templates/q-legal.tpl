#! Q. Legal, compliance, and governance — templates do not constitute legal advice.

=== contract-summary
name: Contract summary
category: Legal, compliance, and governance
icon: contract
description: Key terms of a contract in plain language.
disclaimer: A note-taking aid, not legal advice. Requirements vary by jurisdiction.
## Contract
text contract "Contract" *
text parties "Parties"
date effective "Effective date"
date end "End / renewal date"
long obligations "Key obligations"
long payment "Payment terms"
long termination "Termination terms"
list risks "Risks / unusual terms"

=== contract-obligation-checklist
name: Contract obligation checklist
category: Legal, compliance, and governance
icon: assignment_late
description: Track what you must do under a contract and when.
disclaimer: A note-taking aid, not legal advice.
aliases: Lease obligation tracker
## Obligations
text contract "Contract" *
table obligations "Obligations" [Obligation|Clause|Due|Done?]

=== legal-research-notes
name: Legal research notes
category: Legal, compliance, and governance
icon: gavel
description: Research question, sources and findings.
disclaimer: Personal research notes, not legal advice.
## Research
text question "Question" *
text jurisdiction "Jurisdiction"
table sources "Sources" [Source|Citation|Relevance]
long findings "Findings"

=== case-brief
name: Case brief
category: Legal, compliance, and governance
icon: balance
description: Facts, issue, holding and reasoning of a case.
## Case
text case_name "Case" *
text citation "Citation"
long facts "Facts"
long issue "Issue"
long holding "Holding"
long reasoning "Reasoning"

=== policy-summary
name: Policy summary
category: Legal, compliance, and governance
icon: policy
description: What a policy requires and who it affects.
## Policy
text policy "Policy" *
text owner "Owner"
date effective "Effective"
long purpose "Purpose"
list requirements "Requirements"
list exceptions "Exceptions"

=== compliance-checklist
name: Compliance checklist
category: Legal, compliance, and governance
icon: verified
description: Requirements and evidence of compliance.
disclaimer: A tracking aid; does not by itself satisfy any regulatory requirement.
## Compliance
text framework "Framework / regulation" *
table reqs "Requirements" [Requirement|Evidence|Status]

=== regulatory-change-log
name: Regulatory change log
category: Legal, compliance, and governance
icon: update
description: Track regulatory changes and their impact.
## Changes
text area "Area" *
table changes "Changes" [Regulation|Change|Effective|Impact|Action]

=== control-assessment
name: Control assessment
category: Legal, compliance, and governance
icon: tune
description: Test whether a control is designed and operating effectively.
## Control
text control "Control" *
text objective "Control objective"
long test "Test performed"
select design "Design effective?" [Yes|No|Partially] = Yes
select operating "Operating effective?" [Yes|No|Partially] = Yes
list findings "Findings"

=== risk-assessment
name: Risk assessment
category: Legal, compliance, and governance
icon: warning_amber
description: Identify hazards or risks, rate them and set controls.
## Assessment
text activity "Activity / area" *
table risks "Risks" [Risk|Likelihood 1–5|Impact 1–5|Controls|Residual]
date review "Review date"

=== data-inventory
name: Data inventory
category: Legal, compliance, and governance
icon: storage
description: What personal data you hold, why and for how long.
## Inventory
text scope "System / process" *
table data "Data" [Data category|Purpose|Lawful basis|Retention|Location]

=== vendor-assessment
name: Vendor assessment
category: Legal, compliance, and governance
icon: storefront
description: Assess a vendor's security, compliance and fit.
aliases: Supplier assessment
## Vendor
text vendor "Vendor" *
text service "Service"
checklist checks "Checks" = "Security certification|Data processing terms|Financial stability|References|Exit plan"
rating risk "Risk rating (5 = highest)" max=5
long notes "Notes"

=== document-retention-checklist
name: Document retention checklist
category: Legal, compliance, and governance
icon: folder_delete
description: How long to keep each document type.
disclaimer: Retention periods depend on your jurisdiction and circumstances.
aliases: Data retention review
## Retention
text scope "Scope" *
table rules "Rules" [Document type|Keep for|Where|Disposal]

=== approval-requirements-reference
name: Approval requirements reference
category: Legal, compliance, and governance
icon: approval
description: Who must approve what, for your own reference.
## Approvals
text organisation "Organisation" *
table matrix "Approval requirements" [Decision / spend|Threshold|Approver]

=== legal-consultation-preparation
name: Legal consultation preparation
category: Legal, compliance, and governance
icon: support_agent
description: Prepare facts, documents and questions before seeing a lawyer.
private: true
disclaimer: Preparation notes only; your lawyer's advice takes precedence.
## Consultation
text matter "Matter" *
long facts "Facts in order"
list documents "Documents to bring"
list questions "Questions"
text outcome "Outcome I'm hoping for"

=== compliance-action-list
name: Compliance action list
category: Legal, compliance, and governance
icon: playlist_add_check
description: Actions needed to close compliance gaps, with evidence of completion.
disclaimer: A tracking aid; does not by itself satisfy any regulatory requirement.
## Actions
text framework "Framework / requirement" *
table actions "Actions" [Action|Gap addressed|Due|Evidence|Done?]
date review "Next review"
