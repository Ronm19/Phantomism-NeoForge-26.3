# Void Phantom #34 - The Abyssal Guardian

Implemented 2026-10-18 for Minecraft 26.3 / NeoForge 26.3.0.64-beta / Java 25 / GeckoLib 5.5.7.

## Companion integration

- Male, Elite: 80 HP, 7 attack, 12 armor, 4 toughness, 55% knockback resistance, 56-block awareness, 9-block/sec nominal flight, 36-tick melee interval, normal scale.
- End Barrens and End Highlands, any time, native spawn weight 3, singleton. The End-only encounter helper checks every 10 seconds, validates loaded visible safe airspace, keeps a local wild singleton cap, and gives successful encounters a 10-minute per-player recovery.
- Wild individuals hunt legitimate hostile mobs, not unprovoking players, villagers, peaceful animals or neutral Endermen.
- Tame with one End Crystal, guaranteed on the first valid attempt. Interaction consumes the item without placing or exploding a crystal.
- Shared Staff, breeding, owner persistence, flock policy, adult riding, rebindable G projectile and Ctrl special-melee routing are retained. Ordered foreign-companion duels remain isolated from observers.
- Shared adult/baby model only; optional accessories are explicitly disabled for this species. Obsidian, dark-violet and lavender saved-template recolors include adult, baby, emissive markings and spawn egg. No image generation or new accessories.

## All ten abilities

| Ability | Implemented behavior and initial balance |
| --- | --- |
| Void Survival | Void-damage immunity only. Server pre-tick rescue uses a previously verified, still-safe loaded position before the below-world boundary. Mounted owner stays attached. Safe self-history expires after five minutes; no resurrection or forced chunk loading. A rapid-second-fall boundary safeguard overrides the ordinary five-second self-rescue spacing when necessary. |
| Null Field | Combat-only 10-block radius, at most 16 eligible enemies per scan. Brief 10% movement and 15% outgoing-damage suppression; no canceled entity ticks or global AI disabling. |
| Void Bolt | Real collision-authoritative projectile: 8 HP, approved 42-block travel budget, 12-second AI history and approved 0.3-second mounted cooldown. Protected allies are ignored; solid terrain stops it without destruction. |
| Void Grasp | One eligible enemy within 18 blocks; 95% ordinary movement restraint for two seconds; 16-second cooldown. Boss/control-immune targets get only 5% outgoing-pressure reduction, not an illegal movement lock. |
| Abyss Anchor | One verified temporary owner/eligible allied-Phantom anchor, 60-second lifetime. Genuine falling/void emergencies only, not ordinary movement. A successful rescue consumes its 40-second cooldown and closes the marker. Failed validation or teleport cancellation consumes nothing. Default automatic protectee is the owner; no additional command UI was introduced. |
| Anti-Caster Pulse | 12-block localized pulse, 18-second cooldown. Stops supported native illager spell warmups, delays opted-in compatible normal cooldowns, or applies two-second movement/offense pressure. Does not disable arbitrary modded abilities or boss phases. |
| Void Seal | One four-second priority seal; 24-second cooldown, 30% ordinary movement reduction and 25% outgoing-damage reduction. One active seal is enforced even if another system resets the cooldown. Bosses resist control and take only 5% offensive pressure reduction. |
| Void Step | Collision- and loaded-corridor-checked dash within 12 blocks, 16-second cooldown, one 1.4x melee hit (9.8 HP before defenses). Passenger-aware destination clearance, native teleport cancellation, full-group rollback on partial passenger veto, and bounded clean-model afterimages. |
| Dark Matter Shield | One reactive six-second barrier on self, owner or an eligible allied Phantom within 20 blocks; 30% incoming hostile damage reduction, 35-second cooldown. Multiple Void shields use the strongest value rather than multiplying together. No blanket environmental immunity. |
| Null Horizon | Exactly 12 seconds, 24-block radius, 100-second cooldown. A genuine crowd of at least three valid hostiles is required. Three scheduled pulses at ticks 100, 160 and 220 deal 4 HP each, at most once per enemy per pulse, at most 32 enemies per pulse. Maximum 12 HP per enemy from the defined barrage; no per-tick damage or terrain destruction. |

The main ability damage/range/cooldown/duration settings live under `voidPhantom.abilities` in the existing common configuration. Shared attribute configuration also includes Void. Ultimate duration/radius/cooldown and the three-pulse schedule preserve the approved design. Control leases are capped at four seconds and followed by a shared 80-tick recovery, including across different Voids and source cancellation; no permanent suppression chains. Chrono cannot reduce the anchor rescue or ultimate cooldown.

### Rescue and casting limitations

Safe history and anchors are intentionally temporary and are not replayed after loading or dimension travel. Destinations are rechecked for dimension, loaded chunks, border, support, fluids, hazards, full rider clearance and occupancy. If no previously verified safe loaded destination remains, the implementation refuses an unsafe teleport rather than fabricating ground, forcing chunks or reviving a removed entity. Deliberate flying/spectator/gliding owner movement is not treated as a rescue emergency. Ordinary no-gravity companion flight is not mistaken for a lethal unsupported fall.

Native illager interruption stops the currently running casting goals; vanilla's remaining casting-busy timer and future cooldown remain respected. Compatible opt-in normal ability slots can be delayed; ultimate/revival slots and unsupported third-party internals are not manipulated. Bosses keep their AI, movement phases and native invulnerability/projectile-immunity rules.

## VFX

Fifteen species-specific effects reuse the existing NeoForge-native packets, culling, quality settings, emissive material path and budgets. Four new textures provide fractured void sigils, angular fragments, diamond cores and smooth trails. The sequence includes restrained combat ambience, readable bindings/seals, a collapsing rescue diamond, dark fractured shields, clean shared-model dash echoes, and Null Horizon's descending sky sigil, opposing rings, inward streams, three impact waves and final contraction/residue.

Void Step echoes reuse the existing accessory-free silhouette, selected only for the Void skin. No shared model geometry or old species accessory defaults were changed. The native submission check measured a maximum 3,244 budgeted vertices for Void effects, including conservative echo costs, within the 4,096 cinematic/afterimage allowance. Ordinary effects retain the 1,024 allowance; shared frame and event budgets remain intact. This is geometry-budget validation, not a live FPS benchmark.

## Files

Created nine Java sources:

- `entity/VoidPhantom.java`, `entity/VoidBolt.java`
- `entity/ai/VoidRescue.java`, `entity/ai/VoidSuppression.java`
- `entity/spawn/VoidEncounterSpawner.java`
- `vfx/VoidVfx.java`
- `client/renderer/VoidPhantomRenderer.java`, `client/vfx/VoidEffects.java`
- `datagen/ModVoidAnimationProvider.java`

Modified only the required integration points: `Config`, `Phantomism`, `PhantomismClient`, `PhantomDiscovery`, `PhantomismContent`, `ModSpawnPlacements`, `ModTags`, six existing datagen providers, `PhantomismNetwork`, `PhantomVfxTheme`, five existing native VFX registration/dispatch/culling/budget files, English translations and the two roster tags. Added eight generated JSON resources and nine Void-only PNGs. No new model file, recipe, non-vanilla taming item or network channel was needed.

Texture integration paths: `assets/phantomism/textures/entity/void_phantom.png`, matching `_baby`, `_eyes`, `_baby_eyes`, `textures/item/void_phantom_spawn_egg.png`, and `textures/vfx/void_{sigil,fracture,core,trail}.png`.

Exact changed-file hashes and inventory: `C:/Users/Hassan Rifi/Documents/Codex/2026-10-10/referenced-chatgpt-conversation-this-is-an/work/phantomism-private/void-implementation/change-manifest.json`.

## Validation and build

- All 274 production Java sources compile against the exact locally installed Minecraft/NeoForge/GeckoLib artifacts on Java 25. The compiler reports deprecation warnings, not compilation errors.
- Native resource generation succeeds: 303 JSON resources; repeat generation is unchanged.
- Void: 312 native gameplay assertions and 25 native End habitat/real-encounter/safety assertions pass, including six-tick mounted history, actual damage/collision, genuine Evoker warmup interruption, shared suppression recovery, single Seal, exact ultimate waves, owner/ally safety, native teleport cancellation, passenger rollback, designated companion flight safety and mounted rescue in the actual End.
- Native client checks pass for all nine PNG decodes, all eleven shared-skeleton clips, all fifteen effects, low-budget fallbacks and bounded clean-model echoes. Existing Chrono, Rift and other species' native effect checks also pass.
- Ten existing recent-species gameplay suites pass: Ancient, Angel, Chrono, Demon, Omen, Primordial, Reaper, Rift, Sentinel and Spectral. Shared regressions pass, including 34-species native VFX synchronization/codecs, mounted controls, native dimension transfers, projectile ranges and Wither's exact fourteen-skull Storm.
- The isolated native servers complete their required GameTest successfully. Validation fixtures remain outside production sources and are not deployed.
- Full Gradle `build --offline --no-daemon` is still blocked before tasks by the local environment error: `java.io.IOException: Unable to establish loopback connection`. No successful Gradle build or newly packaged JAR is claimed. Tested development classes and resources are refreshed separately.

## Regression audit, backup and remaining manual checks

Hash audit confirms all 244 pre-existing PNGs, all seven existing shared model files, all 43 existing renderer sources and all 110 existing entity/AI sources are unchanged. No global horns/accessories, unrelated geometry changes or replacement textures. Primordial's current stone/gold reskin and spawn tuning are preserved.

Pre-change project source/resource/build-script snapshot: `C:/Users/Hassan Rifi/Documents/Codex/2026-10-10/referenced-chatgpt-conversation-this-is-an/work/phantomism-private/void-implementation/phantomism-before-void-source-snapshot.zip`. It excludes generated build caches, IDE state and game saves; it is not a world backup.

Restart the development client to load the new entity and effects. Still manually verify adult/baby appearance, actual GPU translucency/afterimage readability, rebindable controls and camera comfort, several simultaneous Null Horizons with real client settings, natural encounters in unmodified terrain, and two-real-client riding/rescue synchronization. Unsupported third-party casting mechanics and unusual externally supplied vehicle graphs require explicit compatibility testing.
