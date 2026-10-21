# Phantom King #36 changelog

## Mounted seat and Royal Ram hotfix

- Lowered the King-only riding attachment to the shared body's back and centered it behind the crown. At default 2x scale, the seat moves approximately 0.29 blocks down and 0.44 blocks forward; offsets rotate with yaw and scale proportionately.
- Fixed `Attempted to move entity on a logical side not permitted to simulate movement` during mounted Ram. A synchronized Ram flag temporarily makes the server authoritative, suppresses competing client prediction/steering and restores normal flight after success, obstruction, target loss, Stay/Retreat, dismount or cleanup.
- The controlling rider receives native metadata and an ordered final vehicle-position correction. No movement-side guard is disabled, movement-type workaround or blind teleport is used, and normal flight authority is unchanged.
- Reproduced the failure with a normally client-authoritative ServerPlayer. Passed 55 targeted native seat/authority/collision/control-handoff checks, 77 King gameplay checks, 63 spawn checks and the King/crown/Chrono/Rift client-data regressions.
- Only `PhantomKing.java` changes gameplay source in this hotfix. Shared seats, model geometry, textures, accessories, stats and ability cooldowns are unchanged.

## Crown-rendering crash hotfix

- Fixed the reported `StackOverflowError`: the crown's weak-key cache invoked `GeoBone.hashCode()`, which recursively visits linked parent/child bones. The model hierarchy itself was valid.
- The crown now caches by object identity, with at most two retained heads for the adult/baby rigs; reloaded model identities evict old entries safely.
- Reproduced the original failure in the actual crown render callback using the baked shared adult model, then passed 827 native callback/cache/visibility/reload assertions after the fix. Existing King, Chrono and Rift client-data checks also pass again.
- No crown removal, texture changes, shared model edits, global accessories or gameplay changes were needed. The fix is included in the refreshed development output and source ZIP.

## Added

- Male Apex Phantom King with canonical stats, true native 2x dimensions, proportional babies and scale-aware rider seat.
- All eleven royal abilities, 0-3 synchronized Momentum, timed melee, physical Ram, temporary focus/formation, conditional defense and three-stage King's Dominion (15 seconds / 40 blocks / 120 seconds).
- King-only physical head crown and nine template-derived texture/VFX PNGs; ten dedicated action animations using the shared rig.
- Entity, attributes, egg, creative entry, discovery, loot, tags, biome spawning, localization and mounted networking.
- Four-biome solitary natural spawning, safe bounded encounter recovery, Staff taming and provisional Enchanted Golden Apple taming.
- Bounded normal-cooldown timing assistance, owner-priority notifications and source-scoped command leases; authorized duels remain isolated.
- Deliberate charge-only, owner-permitted log/leaf breaking, maximum eight blocks, respecting native protection hooks.
- Native gameplay, spawn, client-resource/geometry and previous-species regression validation, a source snapshot, manifest and implementation report.

## Corrected during validation

- Refreshed native dimensions at construction so SCALE 2.0 produces a genuinely doubled hitbox immediately, including natural spawns.
- Cleared transient royal sessions on loss/change of ownership, not only ordinary death/removal.
- Ensured intercept claims are taken only after safe movement is feasible, and directive capacity is checked before changing an ally's target.
- Aligned the generic King's VFX theme with the discovery roster key.
- Restored a temporarily malformed Dark biome JSON encountered during validation to the byte-identical original snapshot. No net Dark spawning changes remain.

## Preserved

- All 262 pre-existing PNGs, all seven shared model/visibility files and all 118 unrelated entity/AI files are byte-identical to the starting snapshot.
- Existing renderers and accessory defaults are unchanged. The crown is only in the King's new render layer; no global horns, new rig bones or accessory variants were added.
- Chrono/Rift native VFX regression checks and the recent-species/shared-system gameplay groups pass.

## Build limitation

Java compilation and native validation pass. Gradle still fails in the machine's loopback/daemon setup before tasks, so the deliverable is an updated source project plus tested local development output, not a newly verified distributable JAR. Live visual, FPS, real multiplayer and survival-balance checks remain outstanding.
