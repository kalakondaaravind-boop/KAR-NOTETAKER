#! AC. Security and reliability

=== security-checklist
name: Security checklist
category: Security and reliability
icon: lock
description: Personal security basics.
## Checklist
checklist accounts "Accounts" = "Password manager in use|Unique passwords|Two-factor on email|Two-factor on banking|Recovery codes stored"
checklist devices "Devices" = "Screen lock|Updates on|Encrypted backups"
date reviewed "Reviewed" = today

=== vulnerability-observation
name: Vulnerability observation
category: Security and reliability
icon: bug_report
description: Record a vulnerability you noticed and its handling.
## Vulnerability
text system "System" *
long description "Description"
select severity "Severity" [Critical|High|Medium|Low] = Medium
text reported "Reported to"
select status "Status" [Open|Reported|Fixed|Accepted] = Open

=== incident-timeline
name: Incident timeline
category: Security and reliability
icon: timeline
description: Timestamped events during an incident.
## Timeline
text incident "Incident" *
table events "Events" [Time|Event|Source]

=== backup-verification
name: Backup verification
category: Security and reliability
icon: backup
description: Prove a backup can actually be restored.
## Verification
text what "What was backed up" *
date day "Date" = today
text backup_location "Backup location"
checklist checks "Checks" = "Backup exists|Restored to clean location|Files open correctly|Counts match"
select result "Result" [Pass|Fail] = Pass

=== access-review
name: Access review for personal accounts
category: Security and reliability
icon: manage_accounts
description: Review which apps and devices can access your accounts.
aliases: Access review
## Review
table accounts "Accounts" [Account|Connected apps / devices|Removed]
date day "Date" = today

=== device-security-checklist
name: Device security checklist
category: Security and reliability
icon: phonelink_lock
description: Security settings for a phone or computer.
## Device
text device "Device" *
checklist settings "Settings" = "OS up to date|Screen lock|Biometrics|Find my device|Encrypted storage|App permissions reviewed"
