# Runbook - Inventory Platform 5.0.2 production deployment

Change window: 2026-05-09 21:00 UTC to 2026-05-10 01:00 UTC. This runbook covers PROJ-501, PROJ-502 and PROJ-503. All three were validated on UAT on 2026-05-06 and are deployed to PROD in one window.

## Scope

- PROJ-501: new stock reservation service, replaces the nightly reservation batch.
- PROJ-502: warehouse sync moves from file drop to the message queue.
- PROJ-503: schema change that adds the reservation expiry column to the stock table.

## Before the window

- The release manager confirms that the change approval is signed and that the on-call engineer is reachable.
- The database administrator confirms that at least 40 GB of disk is free for the snapshot and for the migration.
- The warehouse team confirms that no stock count is planned during the window.
- The deployment engineer confirms that the registry holds the signed image for the inventory API and for the reservation service, and that the image digests match the build record.
- The on-call engineer confirms that the dashboards for the API, the queue and the database load, and that the alert routes reach the on-call phone.
- The release manager confirms that the customer support team knows the window, the expected maintenance page and the name of the person to call if customers report errors.
- Everyone confirms that the rollback section of this runbook has been read, including the point of no return in the deploy steps.

## Roles and communication

- The release manager owns the window, opens and closes it in the release channel, and is the only person who can stop the deployment.
- The deployment engineer executes the steps below in order and reports every completed step in the release channel, so that the log can be reviewed afterwards.
- The on-call engineer watches the dashboards for the API, the message queue and the database, and reports any alert that fires during the window.
- The database administrator is on call for the snapshot and the migration, and is the only person who restores a snapshot.
- Status updates go to the stakeholders every 30 minutes, even when nothing has changed. A silent channel is treated as a problem.

## Deploy

1. Announce the start of the window in the release channel and pause the warehouse sync schedule.
2. Put the inventory API in maintenance mode: `kubectl annotate ingress inventory-api maintenance=on`.
3. Wait until the message queue backlog is empty: `rabbitmqctl list_queues name messages`. If the backlog does not drain within 15 minutes, stop and call the release manager.
4. Take a database snapshot: `pg_dump --format=custom --file=/backup/inventory-pre-5.0.2.dump inventory`. Check that the file is larger than 10 GB before you continue.
5. Scale the sync workers to zero: `kubectl scale deploy/inventory-sync --replicas=0`.

> **Point of no return**: after the next step the old version cannot read the migrated schema.

6. Apply the schema migration: `flyway migrate -target=57`. On the production table this takes about 25 minutes.
7. Roll out the new API: `kubectl set image deploy/inventory-api api=registry.example.test/inventory-api:5.0.2`.
8. Wait for the rollout: `kubectl rollout status deploy/inventory-api`.
9. Roll out the reservation service: `kubectl apply -f deploy/reservation-service.yaml`.
10. Scale the sync workers back up: `kubectl scale deploy/inventory-sync --replicas=6`.
11. Leave maintenance mode: `kubectl annotate ingress inventory-api maintenance=off --overwrite`.
12. Run the smoke test: `./smoke.sh PROD`. It creates one reservation and releases it again.
13. Resume the warehouse sync schedule and watch the queue for 10 minutes.
14. Confirm PROJ-501 on PROD and announce the end of the window.

## Post-deploy validation

- A reservation can be created, extended and released.
- The warehouse sync delivers stock levels through the queue and no file is left in the drop folder.
- The stock report shows the expiry column and no error appears in the API log.
- The database administrator confirms that the migration left no invalid index and no long running lock on the stock table.

## Rollback

- Trigger: error rate above 2% for 5 minutes after the API is back in service.
- Trigger: the smoke test fails twice, or the queue backlog grows for more than 10 minutes.
- Decision owner: the release manager decides; the on-call engineer executes.
- Before the migration: `kubectl rollout undo deploy/inventory-api`, then leave maintenance mode.
- After the migration: restore `/backup/inventory-pre-5.0.2.dump` with `pg_restore --clean --dbname=inventory`, then redeploy 5.0.1 and resume the old reservation batch.
