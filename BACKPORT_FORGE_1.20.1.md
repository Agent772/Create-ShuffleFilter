# Forge 1.20.1 Backport Plan

Greenfield Forge 1.20.1 backport of the current 1.21.1 (main) feature set.
The existing `1.20.1` branch is not extended; only its build scaffolding is referenced.
Feature logic is ported from `main`.

## Key Decisions

- **Create version**: Create 6.x (same line as main) - Create 6 ships for Forge 1.20.1
- **Mappings**: mojmap (parchment optional as a parameter-name overlay)
- **Java**: 17 (Forge 1.20.1 target)
- **No backward compatibility**: no prior 1.20.1 release exists, so no migration paths needed
- **Deprecated `ShuffleMode` enum**: dropped entirely

## Epic Overview

Ordered by dependency. Later epics assume earlier ones are merged.

| # | Epic | Issue | Depends On |
|---|------|-------|------------|
| 1 | Build scaffolding & project skeleton | [#7](https://gitea.matejhoz.com/Agent772/Create-ShuffleFilter/issues/7) | None |
| 2 | NBT-backed item data layer | [#8](https://gitea.matejhoz.com/Agent772/Create-ShuffleFilter/issues/8) | #7 |
| 3 | Item, menu & networking port | [#9](https://gitea.matejhoz.com/Agent772/Create-ShuffleFilter/issues/9) | #8 |
| 4 | GUI screens & widgets | [#10](https://gitea.matejhoz.com/Agent772/Create-ShuffleFilter/issues/10) | #9 |
| 5 | Mixin retargeting | [#11](https://gitea.matejhoz.com/Agent772/Create-ShuffleFilter/issues/11) | #10 |
| 6 | Resources & data | [#12](https://gitea.matejhoz.com/Agent772/Create-ShuffleFilter/issues/12) | #9 |
| 7 | JEI integration | [#13](https://gitea.matejhoz.com/Agent772/Create-ShuffleFilter/issues/13) | #10 |
| 8 | Release plumbing | [#14](https://gitea.matejhoz.com/Agent772/Create-ShuffleFilter/issues/14) | All |

## Risks

- **Create 6 on Forge 1.20.1**: verify exact artifact coordinates on `maven.createmod.net`
- **Registrate / Ponder / Flywheel pins**: must be Forge 1.20.1 builds
- **Mixin drift**: Create 6 internals on 1.20.1 differ from 1.21.1; each mixin must be re-validated
- **Recipe schema**: 1.20.1 recipe JSON differs subtly from 1.21.1
- **JEI version**: 1.20.1 uses JEI 15.x, not 19.x

## Out of Scope

- Data migration from any prior 1.20.1 alpha
- Carrying forward the deprecated `ShuffleMode` enum
- Multi-loader (Architectury) refactor
