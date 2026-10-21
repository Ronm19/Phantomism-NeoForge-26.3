# Phantom King final implementation and validation

## Follow-up: mounted seat and movement authority

The riding screenshot showed the King-specific seat above the visible back. The seat now uses the shared body geometry rather than 65% of collision height: `0.44 * native scale` up and `0.22 * native scale` forward, rotated with yaw. At default scale 2 it is about 0.29 blocks lower and centered 0.44 blocks forward behind the crown. The native player vehicle-attachment adjustment is preserved. No other species' seat or model is modified.

The new crash was reproduced in `tickRam` using a normally client-authoritative ServerPlayer with the native IDE movement guard enabled. Earlier mounted fixtures explicitly used a server-authoritative test rider, masking this case. The fix adds a synchronized Ram state, with matching server/client authority decisions during the burst, no competing ordinary travel, and ordered native metadata/vehicle-position handoff back to normal flight. Collision-checked physical movement remains server-controlled, not replaced by a teleport or a different movement type to bypass checks. Completion, obstruction, target loss, commands, dismount and transient cleanup release the takeover.

Final automated results: 55 new native mounted-seat/movement/packet-order assertions, all 77 existing King gameplay assertions, all 63 encounter assertions, 827 crown render-path assertions, and the existing King (2,875,769), Chrono (674,090) and Rift (3,343,711) client-data assertions pass. Seat checks cover four headings, several native scales and actual passenger attachment; movement checks cover a successful 28-base hit, walls, target invalidation, Stay, dismount/remount, metadata propagation and restoration of normal authority. These are isolated native tests, not a new graphical playthrough or real-latency multiplayer test.

Only `PhantomKing.java` changes production gameplay source in this follow-up. All other source/resources, including the crown crash fix and shared textures/models/accessories, are preserved. Corrected development output and the updated source ZIP include both fixes. Restart the development client; existing Kings and worlds do not need deletion. The earlier Gradle packaging limitation is unchanged; no new distributable JAR is claimed.

## Follow-up: crown rendering crash

The reported rendering `StackOverflowError` was traced to the King's `WeakHashMap<GeoBone, ...>` crown cache. `GeoBone.hashCode()` includes both parent and children, so an ordinary linked hierarchy recursively hashes itself when used as a structural map key. This was a cache-key error in the new crown layer, not an invalid shared rig.

The production fix changes only `KingCrownLayer.java`: a bounded identity-keyed map avoids calling bone `hashCode`/`equals` and retains at most two adult/baby head identities, evicting old entries on model reload. The physical crown and all model/texture/accessory choices remain intact.

The supplied crash was reproduced before the change through the actual per-bone crown render callback with the real baked adult model. Afterward, 827 native assertions pass across adult/baby rendering, repeated cache reuse, invisibility and 24 model reloads, with exactly 224 crown vertices emitted per visible callback. The earlier geometry-only checks did not exercise this cache path; the new regression explicitly does. King (2,875,769), Chrono (674,090) and Rift (3,343,711) client-data assertions pass again. These are programmatic native callbacks, not a graphical world playthrough.

All existing PNGs, shared rig/model files and unrelated source remain unchanged by this hotfix. The corrected crown class is deployed to the local development output and included in the refreshed source ZIP. Restart the development client before loading the world; no entity or world deletion is required. The previously reported Gradle packaging limitation is unchanged, and no new installable JAR is claimed.

Phantom King #36 is implemented in the production project. All eleven named abilities, canonical Apex attributes, real native 2x dimensions, adult riding, Staff/food taming, breeding integration, bounded royal leadership and cinematic King's Dominion are present. The crown is a King-only head layer on the unchanged shared rig. The supplied template recolor was previewed; no image generation was used.

## Confirmed automated results

- All production Java sources compile with Java 25 against the project's actual Minecraft/NeoForge/GeckoLib dependencies. Validation sources also compile.
- 77 King native gameplay assertions pass: real damage, 7-tick Strike window, ordinary bite fallback, physical Ram, passenger retention, exact cooldowns, Momentum gain cap/decay, normal-cooldown-only timing credit, owner priorities/formation, strongest-only mitigation, actual allied damage bonuses, intercept reservation, boss-limited Roar, ultimate duration/radius/finisher, native block-break veto, ownership cleanup, persistence, warnings, duel isolation and autonomous live Brain pursuit.
- 63 actual encounter assertions pass: all four registered habitats in day and night, solitary cap, full 2x collision volume, canopy/livestock placement, ten-minute recovery, unsafe/unloaded locations, spectators, mob-spawning gamerule and excluded dimensions. Generated weighted spawn entry is weight 3, group 1.
- 2,875,769 King native client-data assertions pass: nine PNGs decode, ten GeckoLib action clips bake, physical crown coordinates/normals are finite, all eighteen VFX types produce finite bounded quads/UVs at all quality levels. Maximum one-effect geometry is 1212 vertices. Counts are programmatic assertions, not playthroughs or measured frames.
- Chrono: 674,090 native client mesh/timeline/animation/texture assertions pass. Rift: 3,343,711 native six-fracture, opacity, atlas, geometry-callback and budget assertions pass. Existing clock/portal effects remain intact in these tests.
- Data generation completes for 318 JSON resources. Only the seven King resources and two expanded roster tags are integrated; semantically identical old generated files are not reformatted.
- Twelve recent species pass isolated gameplay regression suites: Ancient, Angel, Chrono, Dark, Demon, Omen, Primordial, Reaper, Rift, Sentinel, Spectral and Void.
- Shared regressions pass: Blood, Echo, Skeleton, Vampire, Gravity, Wither, mounted speed, projectile range, VFX networking and dimension travel. VFX networking checks include all 36 themes, native packet codecs and nearby-recipient delivery (2904 assertions). The inherited travel matrix still covers 23 species across three dimensions (1245 assertions); it is not falsely reported as a 36-species travel playthrough.
- Each native server group finishes with the built-in smoke test passing; detailed suite markers, rather than the single smoke-test count, establish the gameplay coverage above.

## Balance and VFX

Official values remain 144HP/14 attack/22 armor/8 toughness/85% knockback resistance/64 awareness/9 flight/1.6-second ordinary melee. Ram: 12 seconds and28 base; Judgment:20 seconds/32 blocks; Roar:25 seconds; Dominion:120-second cooldown/15-second duration/40-block radius.

Provisional values: alternative tame is one Enchanted Golden Apple (100%); spawn weight3/any time; Presence +8% allied offense and8% mitigation; Strike6 seconds and1.35x +.12x per Momentum; Intercept9 seconds; Command20 seconds; Guard28-second cooldown/4 seconds/25%; Army +15% King offense/20% self-mitigation; final hit24-30 base. Roar reduces ordinary enemy offense15% for1.5 seconds and resistant bosses5%, with recovery. All are initial tuning, not proven balance.

The native royal sequence has Coronation (crown, concentric seals, temporary banners), Army (real allied commands/timing and gold trails) and one Final Decree (seal/shockwave/marked-enemy damage). No duplicate allies or global accessory infrastructure are introduced. Existing global8192-vertex budget remains unchanged.

## Audit and limitations

Hash audit: all262 existing PNGs, seven shared model/visibility files and118 unrelated entity/AI source files are unchanged. No existing renderer, global accessory default or model geometry is modified. The only shared entity change is the scoped owner-command/temporary formation hook. No files are deleted. A stray W encountered in Dark's biome JSON during validation was removed; the final Dark file is byte-identical to the initial snapshot, with no net Dark gameplay/resource changes.

The full Gradle build was attempted using Java25 and cached dependencies. It still fails before tasks with `java.io.IOException: Unable to establish loopback connection`. Compilation errors are resolved, but a successful Gradle build and a new distributable JAR are NOT claimed. Tested production classes/resources are deployed to the existing development output only; test classes are not deployed. Restart the development client. Another launcher needs a newly built JAR once the Gradle environment is repaired.

Live graphical combat/animation/crown placement, first-person rider camera clearance, several simultaneous ultimates, measured FPS, real multiplayer latency and survival balancing remain unverified. Native server/data checks and the static template preview do not establish those observations. Existing-species regression groups were isolated from unrelated old world-mutating spawn fixtures.

## In-game instructions

1. Restart the development client. `/summon phantomism:phantom_king ~ ~2 ~` or `/give @s phantomism:phantom_king_spawn_egg`.
2. Tame with the Phantom Staff or one Enchanted Golden Apple. Use the existing empty-hand adult riding interaction.
3. Mounted Ctrl selects Strike close-up or Ram farther away; mounted G uses20-second Judgment, not a new projectile.
4. Supply at least three valid hostile mobs within40 blocks in the open and let the King fight to observe the full15-second Dominion.
5. Check Follow/Stay/Attack/Retreat/HOME and work orders with several owned allies; owner decisions must win. Test an authorized foreign-pet duel without recruiting bystanders.
6. Check permitted wood breaking only during Momentum2+ Ram, native protection cancellation and `mobGriefing=false`. Flying normally must never break blocks.
7. Natural encounters: Windswept Hills, Old Growth Pine Taiga, Old Growth Spruce Taiga and Meadow, day or night, solitary and rare.

## Deliverables

The updated source-project ZIP includes source/generated resources, Gradle wrapper/build definitions, project documentation, the precise change manifest and isolated King/Chrono/Rift validation sources plus final logs. It excludes personal worlds, runtime state, IDE/Git/Gradle caches, downloaded dependencies and compiled development output. It is a source snapshot, not an installable mod JAR. The full ability/status/tuning reference is PHANTOM_KING.md; changes are summarized in CHANGELOG_PHANTOM_KING.md.

## Exact source/resource file changes

### Modified
- `src/generated/resources/data/phantomism/tags/entity_type/phantoms.json`
- `src/generated/resources/data/phantomism/tags/item/phantom_spawn_eggs.json`
- `src/main/java/net/ronm19/phantomism/Config.java`
- `src/main/java/net/ronm19/phantomism/Phantomism.java`
- `src/main/java/net/ronm19/phantomism/PhantomismClient.java`
- `src/main/java/net/ronm19/phantomism/advancement/PhantomDiscovery.java`
- `src/main/java/net/ronm19/phantomism/client/vfx/VfxGroup.java`
- `src/main/java/net/ronm19/phantomism/client/vfx/VfxManager.java`
- `src/main/java/net/ronm19/phantomism/client/vfx/VfxMesh.java`
- `src/main/java/net/ronm19/phantomism/client/vfx/VfxRegistry.java`
- `src/main/java/net/ronm19/phantomism/datagen/ModBiomeModifierProvider.java`
- `src/main/java/net/ronm19/phantomism/datagen/ModBiomeTagsProvider.java`
- `src/main/java/net/ronm19/phantomism/datagen/ModDataGenerators.java`
- `src/main/java/net/ronm19/phantomism/datagen/ModEntityTypeTagsProvider.java`
- `src/main/java/net/ronm19/phantomism/datagen/ModItemTagsProvider.java`
- `src/main/java/net/ronm19/phantomism/datagen/ModModelProvider.java`
- `src/main/java/net/ronm19/phantomism/entity/AbstractPhantom.java`
- `src/main/java/net/ronm19/phantomism/network/PhantomismNetwork.java`
- `src/main/java/net/ronm19/phantomism/registry/ModSpawnPlacements.java`
- `src/main/java/net/ronm19/phantomism/registry/PhantomismContent.java`
- `src/main/java/net/ronm19/phantomism/util/ModTags.java`
- `src/main/java/net/ronm19/phantomism/vfx/PhantomVfxTheme.java`
- `src/main/java/net/ronm19/phantomism/vfx/VfxPacket.java`
- `src/main/resources/assets/phantomism/lang/en_us.json`

### Added
- `src/generated/resources/assets/phantomism/geckolib/animations/entity/phantom_king.animation.json`
- `src/generated/resources/assets/phantomism/items/phantom_king_spawn_egg.json`
- `src/generated/resources/assets/phantomism/models/item/phantom_king_spawn_egg.json`
- `src/generated/resources/data/phantomism/advancement/discovery/phantom_king.json`
- `src/generated/resources/data/phantomism/loot_table/entities/phantom_king.json`
- `src/generated/resources/data/phantomism/neoforge/biome_modifier/phantom_king_spawns.json`
- `src/generated/resources/data/phantomism/tags/worldgen/biome/phantom_king_spawns.json`
- `src/main/java/net/ronm19/phantomism/client/renderer/KingCrownLayer.java`
- `src/main/java/net/ronm19/phantomism/client/renderer/PhantomKingRenderer.java`
- `src/main/java/net/ronm19/phantomism/client/vfx/RoyalEffects.java`
- `src/main/java/net/ronm19/phantomism/datagen/ModKingAnimationProvider.java`
- `src/main/java/net/ronm19/phantomism/entity/PhantomKing.java`
- `src/main/java/net/ronm19/phantomism/entity/ai/RoyalRamBreaking.java`
- `src/main/java/net/ronm19/phantomism/entity/ai/RoyalTactics.java`
- `src/main/java/net/ronm19/phantomism/entity/spawn/KingEncounterSpawner.java`
- `src/main/java/net/ronm19/phantomism/vfx/RoyalVfx.java`
- `src/main/resources/assets/phantomism/textures/entity/phantom_king.png`
- `src/main/resources/assets/phantomism/textures/entity/phantom_king_baby.png`
- `src/main/resources/assets/phantomism/textures/entity/phantom_king_baby_eyes.png`
- `src/main/resources/assets/phantomism/textures/entity/phantom_king_eyes.png`
- `src/main/resources/assets/phantomism/textures/item/phantom_king_spawn_egg.png`
- `src/main/resources/assets/phantomism/textures/vfx/royal_crown.png`
- `src/main/resources/assets/phantomism/textures/vfx/royal_seal.png`
- `src/main/resources/assets/phantomism/textures/vfx/royal_standard.png`
- `src/main/resources/assets/phantomism/textures/vfx/royal_trail.png`

### Documentation added
- `PHANTOM_KING.md`
- `CHANGELOG_PHANTOM_KING.md`
- `PHANTOM_KING_TEST_REPORT.md`
- `PHANTOM_KING_CHANGE_MANIFEST.json`
