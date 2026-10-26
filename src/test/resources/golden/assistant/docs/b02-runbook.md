# Runbook - Orders Platform 4.2.1 production deployment

Change window: 2026-03-14 22:00 UTC to 2026-03-15 00:00 UTC.

## Scope

- PROJ-412: validated on UAT on 2026-03-12.
- PROJ-415: deploys on PROD at 2026-03-14 22:00 UTC.

## Deploy

1. Announce the start of the window in the release channel.
2. Put the API in maintenance mode: `kubectl annotate ingress orders-api maintenance=on`.
3. Take a database snapshot: `pg_dump --format=custom --file=/backup/orders-pre-4.2.1.dump orders`.
4. Scale the workers to zero: `kubectl scale deploy/orders-worker --replicas=0`.

> **Point of no return**: after the next step the old version cannot read the migrated schema.

5. Apply the schema migration: `flyway migrate -target=42`.
6. Roll out the new API: `kubectl set image deploy/orders-api api=registry.example.test/orders-api:4.2.1`.
7. Wait for the rollout: `kubectl rollout status deploy/orders-api`.
8. Scale the workers back up: `kubectl scale deploy/orders-worker --replicas=4`.
9. Leave maintenance mode: `kubectl annotate ingress orders-api maintenance=off --overwrite`.
10. Run the smoke test: `./smoke.sh PROD`.
11. Confirm PROJ-415 on PROD and announce the end of the window.

## Post-deploy validation

- Order creation succeeds and the invoice number is unique.
- The worker queue drains within 10 minutes.
- No new errors in the API log.

## Rollback

- Trigger: error rate above 2% for 5 minutes after the API is back in service.
- Trigger: the smoke test fails twice.
- Decision owner: the release manager decides; the on-call engineer executes.
- Before the migration: `kubectl rollout undo deploy/orders-api`, then leave maintenance mode.
- After the migration: restore `/backup/orders-pre-4.2.1.dump` with `pg_restore --clean --dbname=orders`, then redeploy 4.2.0.
