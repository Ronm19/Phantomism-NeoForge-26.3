# Mirage Phantom #38 - Validation Report

Date: 2026-10-21. Target: Minecraft 26.3, NeoForge 26.3.0.64-beta, Java 25, GeckoLib 5.5.7.

## Build result

All **312 production Java sources** compile together against the cached native game/NeoForge/GeckoLib libraries. Validation fixtures compile separately and are not installed as production classes.

The complete offline Gradle build was attempted against the staged project. It fails before tasks with `java.io.IOException: Unable to establish loopback connection`. This is a host/Gradle startup blocker, not a successful full build. No newly built release JAR is delivered. Tested development classes/resources are installed separately so the existing development client can load them after restart.

## Automated native gameplay

**247 Mirage assertions across 14 scenarios pass** in an isolated actual Minecraft/NeoForge server, not mocked entity classes:

1. Attributes, roster entry 38, actual Staff taming, Glow Ink Sac first-roll behavior and deliberate failed-roll second-attempt pity, native adult riding, baby restrictions and breeding.
2. Shared visual/physical personal limits, six-copy ultimate limit and twelve-copy cross-source owner budget.
3. Actual allied disguise snapshots for every current species, correct King/Golem visual scales, bounded collider/health and disabled copied damage.
4. Real supported hostile target redirection, target restoration, switch cooldown surviving destruction, civilian/boss exclusions.
5. Single consumed False Opening bonus, ordinary melee cooldown, actual native projectile hits, exact 39-block total travel and friendly-fire filtering.
6. Real hostile-arrow interception, friendly-arrow native travel, and visual-copy shattering without physically stopping the hostile arrow.
7. Actual incoming damage absorbed by physical Decoy Rescue, controlled health expenditure and no forced player movement.
8. Safe unmounted Mirror Guard exchange, capped fallback, cooldown and solid-wall rejection.
9. Real client-authoritative ServerPlayer fixture, server-owned mounted Strike, once-only damage, passenger positioning, release packet and independent mounted/AI Bolt clocks.
10. Exact Field radius/duration, Hall 12-second lifecycle, 20-block radius, six-copy limit, once-only finale, return to normal cap, and unreduced 90-second ultimate cooldown.
11. Visual/physical destruction, exact lifetime, cancellation, HOME, dimension-departure removal, caster death, logout, cooldown persistence and no added native loot/XP.
12. Authorized foreign-pet duel remains isolated from observer Mirage protection.
13. Actual loaded-biome custom spawning in Flower Forest at both day and night; registered habitat tags cover Meadow and Dark Forest too.
14. Live Brain acquisition of a genuine owner threat, real attack/deployment and combat eye state.

The same run re-passes **1,090 Golem**, **77 King gameplay**, and **55 King mounted-authority** assertions. The native required game test also completes successfully.

## Automated native client/resource validation

- **2,256,572 Mirage assertions:** native PNG decoding; real GeckoLib animation bakes and fallback resolution; all current species' adult/baby disguise textures; finite geometry, complete quads, valid UVs and low-budget fallback at all exercised qualities/timeline samples. These are geometry/resource assertions, not millions of gameplay sessions.
- **255 actual disguised crown/halo callback assertions:** linked baked bones, identity cache bounds, repeated resource reload, visibility, crown-only King behavior and one-top-face Angel halo rendering.
- Existing King crown: **827**; Chrono VFX: **674,090**; Rift VFX: **3,343,711**; Golem VFX: **1,373,474** assertions re-pass.
- Mirage peaks at **852 vertices per exercised effect**; existing performance budgets are unchanged. These are mesh-budget checks, not measured in-game FPS.
- Native data generation completes. Mirage's generated animation file is included, and new item/biome/tag output matches the native providers.
- Saved-template texture preview was visually inspected on the shared geometry. This preview is not a screenshot from a running game client.

## Previous-species and shared-system regressions

Re-ran native gameplay suites for Ancient, Angel, Chrono, Dark, Demon, Omen, Primordial, Reaper, Rift, Sentinel, Spectral and Void. All pass.

Re-ran Blood, Echo, Gravity, Skeleton, Vampire, Wither, mounted speed, projectile range, all-species dimension travel and native VFX/packet delivery suites. All pass. Dimension-travel coverage includes the new roster entry; native VFX validation covers all **38** themes. These suites run on current production classes, with only the fixtures' expected roster counts updated from 37 to 38.

## Appearance/accessory regression audit

- **282 pre-existing PNGs are byte-for-byte unchanged.** No other Phantom texture was replaced.
- **Seven existing shared model files are byte-for-byte unchanged.** No horns, bones, global accessories or giant collision geometry were added.
- `AbstractPhantom`, `PhantomKing`, `GolemPhantom`, `PhantomismAccessoryVisibility`, `PhantomGeoModel`, `KingCrownLayer` and `AngelHaloLayer` remain byte-for-byte unchanged.
- The shared render state only adds a default-false Mirage combat-eye flag; the shared renderer explicitly resets it for unrelated entities. No existing accessory defaults are altered.
- Existing optional accessories are copied only into authorized decoy presentation profiles. Mirage herself uses `NO_ACCESSORIES`.
- The existing **The Titanium Colossal** advancement title remains unchanged.
- No source/resource files are removed. The manifest records 32 new source/resource files, 23 narrowly modified integration files and 926 untouched existing files.

## Interactive/manual checks still required

These have **not** been performed and are not represented as passed:

- On-screen appearance of Mirage and her copies in a real running client, including camera clipping, depth sorting, translucent panels, combat eyes and player visibility.
- A real two-client multiplayer session: remote rider motion, late-arriving clients, synchronized disguised actors/poses, logout/rejoin and actual network latency.
- Real-world Flower Forest/Meadow/Dark Forest encounter frequency with other mods, weather, natural mob caps and the user's existing world.
- Practical Controller balance, all Staff actions/rebinds under normal play, and compatibility with third-party AI mods. Arbitrary bosses are intentionally not deceived.
- Actual FPS/frame-time measurements with several Mirage owners and reduced particle settings.
- Full Gradle build and a release JAR once the host loopback startup problem is resolved.

Restart the development client to load the newly installed classes and resources. Source archive is a source snapshot, **not an installable release JAR**.
