# Phase 15: Products, Clients & Installations - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-20
**Phase:** 15-products-clients-installations
**Areas discussed:** Products on frozen releases, Install record shape, Where things live in the UI, Per-client 'runs' view

---

## Products on frozen releases

| Option | Description | Selected |
|--------|-------------|----------|
| Any time, guarded | Editable OPEN and FROZEN; removal blocked once installs exist | ✓ |
| Only while OPEN | Legacy frozen releases could never get products | |
| OPEN plus one-time backfill | Frozen release with no products may be set once | |

| Option | Description | Selected |
|--------|-------------|----------|
| Mark stale, as for OPEN | Product change bumps items_changed_at everywhere | ✓ |
| Don't mark frozen docs stale | Product tag on frozen release is metadata only | |

| Option | Description | Selected |
|--------|-------------|----------|
| Optional picker on create + edit on detail | | ✓ |
| Detail page only | | |
| Required on create | | |

## Install record shape

| Option | Description | Selected |
|--------|-------------|----------|
| SUCCESS / FAILED / ROLLED_BACK + VOIDED correction | Mirrors promotion | ✓ |
| Installed only + VOIDED | Failed attempts not recorded | |

| Option | Description | Selected |
|--------|-------------|----------|
| Latest effective SUCCESS by install time | Tie-break recorded_at | ✓ |
| Latest recorded row | Entry order wins | |

| Option | Description | Selected |
|--------|-------------|----------|
| Supersede with edited fields or void | Like promotion corrections, reason required | ✓ |
| Void only, then re-record | | |

| Option | Description | Selected |
|--------|-------------|----------|
| History kept, no new use | Unarchivable | ✓ |
| Fully usable, just hidden from pickers | | |

## Where things live in the UI

| Option | Description | Selected |
|--------|-------------|----------|
| New 'Portfolio' nav page | Products and Clients tabs | ✓ |
| Settings section | Next to environment ladder | |

| Option | Description | Selected |
|--------|-------------|----------|
| Release detail page | Client installs section, bulk by ticking clients | ✓ |
| Both release detail and client page | | |
| Client page only | | |

| Option | Description | Selected |
|--------|-------------|----------|
| All or nothing | One transaction, name offending clients | ✓ |
| Save the valid ones | Partial success | |

## Per-client 'runs' view

| Option | Description | Selected |
|--------|-------------|----------|
| Client detail on the Portfolio page | Products with current release + history | ✓ |
| A column in the Clients list | | |

| Option | Description | Selected |
|--------|-------------|----------|
| 'Not installed' | Explicit row | ✓ |
| Hide the product row | | |

| Option | Description | Selected |
|--------|-------------|----------|
| No; archive the client instead | FK blocks it anyway | ✓ |
| Yes, soft-unlink | Extra schema | |

## Claude's Discretion

Template placeholder shape for products, case-insensitive unique names, length limits, sort orders, endpoint/DTO shapes, audit entries.

## Deferred Ideas

- Recording installs from the client page.
- Soft-unlink of client from product.
