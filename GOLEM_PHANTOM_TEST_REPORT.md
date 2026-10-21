# Golem Phantom #37 - Implementation and validation report

## Delivery

The male Apex Golem companion, all eleven abilities, shared-model titanium adult/baby skins, synchronized calm/combat eyes, persistent village encounters, Staff work controls, 27-slot cargo, transactional delivery, riding, animations and native VFX are implemented in the existing project. `GOLEM_PHANTOM.md` documents controls, every ability, provisional values and safety limits. `GOLEM_PHANTOM_CHANGE_MANIFEST.json` lists every source/resource change.

Source/resource audit: 26 created, 20 modified, zero removed; 903 pre-existing files unchanged. All 271 pre-existing PNGs remain byte-identical. Shared model geometry, accessory defaults, AbstractPhantom, PhantomKing and KingCrownLayer remain byte-identical. The only common renderer addition is the Golem-specific eye-state hook, explicitly reset for other species. No horns, global accessory enabling, replacement textures or unnecessary model parts were introduced.

## Build result

All production Java sources compile successfully with Java25 against the actual cached patched Minecraft26.3, NeoForge26.3.0.64-beta and GeckoLib5.5.7 libraries. Validation sources also compile successfully. Native data generation and the isolated GameTestServer finish successfully.

The requested full offline Gradle build was attempted, but fails before task execution with `java.io.IOException: Unable to establish loopback connection`. This repeats the host's earlier Gradle IPC blocker, not a reported source compilation error. No successful Gradle build or newly built release JAR is claimed. Tested main development classes/resources are deployed separately; no private validation classes are deployed. The ZIP is a source-project snapshot, not an installable release JAR.

## Native server tests actually run

Twelve grouped Golem scenarios cover attributes, scaled hitbox/seat, baby/breeding, Staff and iron-block taming; real hook/fist damage and independent mounted/AI timers; client-authoritative riding/Ram control handoff; ordinary melee, boss-safe Slam and one actual follow-up; regeneration, ordinary/special arrow classification, strongest group guard and expiry; precision, area and deliberate obsidian mining, fluids, containers, unbreakable blocks and native protection vetoes; exact-slot NBT cargo, partial/full transactional delivery, actual chest/owner transfer, missing chests and native access cancellation; Earthbreaker/Skyfall with exact timers and passenger position; deterministic SavedData and an actual occupied-HOME village spawn/removal/reroll test; commands, owner first-hit protection and foreign-pet-duel isolation; genuine shared-Brain mining and ordinary pursuit; full-cargo insertion rollback and one-time death drops.

The 1090 Golem assertions include a 1000-village deterministic decision matrix, not 1090 separate live playthroughs. The village fixture explicitly enables natural spawning because GameTest worlds disable it by default. Test worlds, mock network captures and NoAI target fixtures are isolated from the user's saves. Ordinary Brain pursuit and integrated work tests enable the real Brain. The rider is an actual client-authoritative ServerPlayer with native wrong-side movement checks enabled, not an override that hides the movement assertion.

- PHANTOMISM_GOLEM_SERVER: 1090 native gameplay assertions passed.
- PHANTOMISM_KING_MOUNTED_GREEN: 55 native seat, real-player movement authority, mounted charge, collision, cancellation, packet ordering and control-handoff assertions passed.
- PHANTOMISM_KING_SERVER: 77 native gameplay assertions passed.

## Native client/data tests actually run

These decode the PNGs with NativeImage, bake action clips with GeckoLib and exercise mesh/timeline/finite-vector/UV and budget fallbacks. Per-coordinate assertions across many frames account for the large counts; they are not a graphical FPS benchmark or a manual Minecraft session. Golem meshes peak at 676 vertices in the exercised paths, within unchanged 1024 ordinary/4096 cinematic limits and the existing global budget.

- PHANTOMISM_CHRONO_CLIENT: 674090 native mesh/timeline/animation/texture assertions passed.
- PHANTOMISM_GOLEM_CLIENT: 1373474 native PNG, animation, VFX mesh, finite-vector and budget assertions passed; maximum 676 vertices.
- PHANTOMISM_KING_CROWN_GREEN: 827 native baked-model, cache, per-bone render-callback, visibility and reload assertions passed.
- PHANTOMISM_RIFT_VFX_CLIENT: 3343711 native geometry, six-fracture, opacity, atlas, callback and budget checks passed; maximum 3172 vertices.

## Earlier-species compatibility

The recent-species native gameplay group and shared-system native group both pass. Tests use the expanded 37-species roster; unrelated particle-count expectations remain unchanged. The existing King real-rider movement and crown cache regressions also pass.

- PHANTOMISM_ANCIENT_SERVER: 345 native gameplay assertions passed.
- PHANTOMISM_ANGEL_SERVER: 322 native assertions passed.
- PHANTOMISM_CHRONO_SERVER: 102 native assertions passed.
- PHANTOMISM_DARK_SERVER: 268 native gameplay assertions passed.
- PHANTOMISM_DEMON_SERVER: 415 native assertions passed.
- PHANTOMISM_OMEN_SERVER: 301 native gameplay assertions passed.
- PHANTOMISM_PRIMORDIAL_SERVER: 409 native gameplay assertions passed.
- PHANTOMISM_REAPER_SERVER: 509 native gameplay assertions passed.
- PHANTOMISM_RIFT_SERVER: 420 native assertions passed.
- PHANTOMISM_SENTINEL_SERVER: 138 native assertions passed.
- PHANTOMISM_SPECTRAL_SERVER: 701 native assertions passed.
- PHANTOMISM_VOID_SERVER: 312 native gameplay assertions passed.

- PHANTOMISM_BLOOD_SERVER: 169 checks passed: all nine abilities, actual lifesteal, native damage, family, riding, persistence and spawning.
- PHANTOMISM_ECHO_SERVER: 208 checks passed: all nine abilities, resource, real native combo timing, collision, no recursion, support leases, riding, orders and persistence.
- PHANTOMISM_GRAVITY_SERVER: 188 native assertions passed: all eight abilities, actual safe forces and damage, taming, mounted controls, persistence, bosses, orders and field cleanup.
- PHANTOMISM_MOUNTED_SPEED: 244 native checks passed: all species, actual owner-ridden travel, preserved Black baseline, Echo/Blood/Vampire forward/strafe/reverse/diagonal, modifier scaling, cleanup and unchanged vertical limits.
- PHANTOMISM_PROJECTILE_RANGE: 384 native checks passed across all eighteen ranged species: real longer-distance hits, old-cap survival, eventual expiry, walls, mounted ownership and protected allies; existing lifetimes suffice.
- PHANTOMISM_SKELETON_SERVER: 299 checks passed: native adaptive arrows, budgets, hit limits, temporary Presence, ten abilities plus Presence, mounted input, commands and persistence.
- PHANTOMISM_VAMPIRE_SERVER: 219 checks passed: all eight abilities, reversible native enemy manipulation, owner-critical Rage, lifesteal, fangs integration, riding, persistence and spawning.
- PHANTOMISM_WITHER_SERVER: 810 native assertions passed: all ten abilities, exact adaptive and fourteen-skull budgets, real collision/damage, boss rules, allies, riding, commands and persistence.
- PHANTOMISM_DIMENSION_SERVER: 1276 assertions passed; all twenty-three species, three dimensions, native mounted groups, policy, cancellation, credits/persistence, safe loaded arrival and retry limits.
- PHANTOMISM_VFX_SERVER: 2984 assertions passed: dedicated-server loading, all 37 themed actions/impacts/trails, actual animation and event hooks, trail cadence, family safety, target-cue deduplication, nearby multiplayer delivery, native packet codecs and emission budget

## Provisional gameplay and intentional limits

- Core values are the user's approved 400HP/16attack/30armor/12toughness/95%knockback/6flight/48awareness/2smelee/1.8scale.
- Flight6 is represented by the configured0.3 native attribute, consistent with existing species. The inherited standardized mounted cruise remains unchanged; its regression fixture measures approximately44.44blocks/second for all species. A literal six-block/second mounted cap is not claimed or silently imposed.
- Physical-melee reduction15%; natural regeneration2HP/5s; guard45%/12blocks/8s/30sCD, strongest Golem only.
- Ram32base/14sCD; Slam22base/6blocks/10sCD plus at most one10base follow-up. Boss/heavy-target launch restrictions reuse the conservative existing movement-immunity rules.
- Hook24base with bounded6base nearby shockwave; fist20base,1.5blocks/tick,42blocks. Mounted hook/fist share0.3s; AI hook/fist share12s independently.
- Titan's Impact has one40base,16-block,24-target-capped impact;10-second effect window/110-second cooldown. No persistent terrain destruction.
- Area excavation is intentionally ore-only plus explicitly enabled obsidian, not unrestricted bulk terrain removal. Precision additionally supports approved natural stone. Enclosed veins can pause until a safe route is exposed.
- Default volume512, maximum4096, axis limit16; work interval12ticks (configurable4-200); obsidian100ticks. Leaf Ram authorization is off by default, capped at8 leaves per charge and never permits logs/buildings.
- Offline or different-dimension owners pause work. Missing/unloaded/full or unauthorized inventories retain cargo. The loaded-route and native permission requirements remain in force while an owner is distant.
- Third-party claims/inventories need to participate in the native permission/capability hooks. No universal historical ownership of natural-looking ore/stone is invented.

## Remaining in-game/manual checks

- Inspect calm/cyan eye presentation, enlarged saddle/first-person camera, animation transitions and sound/VFX readability on the actual graphical client.
- Verify timing/readability of natural airborne combos against freely moving enemies and real-world terrain, not held target fixtures.
- Visit generated villages across chunk/world reloads; the occupied-HOME native encounter and SavedData codec are automated, but a full generated-world visual encounter playthrough is not claimed.
- Test two updated remote clients, latency, reconnects and actual shader/render/FPS performance. Native server authority, packet captures/codecs and finite mesh budgets are tested, not a real networked two-player session.
- Verify any installed claim/inventory mods with their particular policies and sided handlers; exercise longer loaded routes and partially unloaded caves in the actual modpack.
- Balance400HP and the provisional damage/mitigation values in normal survival play.
