# Release notes - Orders Platform 4.2.1

Relase date: 2026-03-14 22:00 UTC. Previous version: 4.2.0.

## New features

- PROJ-101: Add retry to the paymnet gateway client, fixes timeouts on PROD
- PROJ-102: Fix duplcate invoice numbers when an invoice is reprinted (UAT verified)
- PROJ-103: Upgrade orders-client to 3.9.4 for the new retry polcy
- PROJ-104: Stop the nightly export from lockin the orders table
- PROJ-105: Show the delivery estmate on the order summary page
- PROJ-106: Correct VAT rounding for credit notes
- PROJ-107: Remove the old /v1/ping endpoint
- PROJ-108: Fix the broken link in the shipment emial template

## Known issues

- Export of more then 10000 orders can take upto 20 minutes. Tracked in PROJ-104.

## Environments

| Environment | Date | Status |
|-------------|------|--------|
| UAT | 2026-03-12 | Deployed and verifed |
| PROD | 2026-03-14 22:00 UTC | Planed |
