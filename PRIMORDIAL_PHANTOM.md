# Primordial Phantom #33 — The First Among Phantoms

Implemented as a female Legendary companion on the existing shared Phantom model, following the user's lore clarification. The repeated Primal Domain panels in the reference describe one ultimate, not two.

## Approved integration

- 88 HP, 10 attack, 14 armor, 6 toughness, 60% knockback resistance, 64-block awareness, 9.5 blocks/second flight, 34-tick ordinary melee, 1.2 scale. Configurable species stats use the existing configuration infrastructure.
- Heart of the World guarantees the first valid tame and consumes one item. Craft one shapelessly from one Heart of the Sea, one Nether Star and one Echo Shard.
- Adult riding, owner persistence, breeding, flock/Brain AI and Phantom Staff commands reuse existing systems. G fires Starfall; the existing rebindable Ctrl special-melee route starts Primordial Strike against a nearby forward hostile. No new hard-coded keys.
- Wild individuals remain neutral unless genuinely provoked. Authorized foreign-companion duels stay local; observing family members do not acquire the foreign companion solely from that duel.
- Stay, HOME, incompatible work tasks, death, unload, dimension changes and invalid owners end active fields/leases. Cooldown history persists, including through actual native dimension copies. Normal combat pursuit/melee continues while specials recover.

## All ten abilities

These are conservative provisional balance choices except where explicitly approved in the reference or conversation. They are not claims of live gameplay balance.

| Ability | Implemented behavior and provisional tuning |
| --- | --- |
| Primordial Aura | 10% incoming hostile damage reduction for self, owner and same-owner Phantoms within 16 blocks and line of sight. Multiple Primordials select the strongest protection rather than stack. Excludes bypass damage and authorized duels. |
| Elemental Harmony | 50% fire, lightning, freezing and Wither damage resistance while enabled; also magic damage while the Poison effect is active, since vanilla poison shares that damage type. Does not remove effects or grant immunity. |
| Cosmic Insight | Owner-only cues for loaded ores in a bounded 64-block gaze corridor, visible hostile threats, an endangered owner and valid loaded structure starts. At most 96 ore samples per check, 81 already-loaded chunks per structure check, 64 cached positions and eight insight packets per owner per second shared across Primordials. No chunk generation or ore broadcasting to other players. |
| Creation Wing | Eight-second protective wings, 24-second cooldown. Defends the nearby owner or self; intercepts at most two eligible incoming ordinary hostile projectiles per four-tick check and 12 per activation. Friendly projectiles, boss shooters, tridents and unsupported projectile types are preserved. Shared short-lived claims choose a nearby active defender. Initial controlled push is zero damage. |
| Reality Shift | Collision-safe approximately seven-block repositioning with star trails, 16-second cooldown and three-second fall safety. Checks the full swept volume, loaded chunks, fluids, world border and height. No automatic rider/passenger teleport or movement through solid walls. |
| Primordial Strike | Three native six-HP hits at ticks 1, 12 and 23; 18 HP total before defenses, 12-second cooldown. Each hit rechecks the same target, range and visibility. Native hurt immunity is preserved; no resetting enemy hurt timers. Ordinary melee remains ten HP every 1.7 seconds. |
| Starfall | Actual server-authoritative ten-HP projectile, approved 42-block primary travel range, 12-second AI cooldown and six-tick/0.3-second mounted cooldown. On impact, creates up to three actual three-HP fragments with their own eight-block travel limit. Children prefer distinct nearby eligible hostiles, exclude the primary victim, and cannot recursively shatter. Terrain collision never edits blocks. |
| World Restoration | Eight-second recovery field, 20-block radius, 30-second cooldown. Restores two HP and removes at most one whitelisted harmful effect per recipient per 40 ticks. The recipient gate is shared across overlapping Primordials and their ultimates. Works on allied Phantoms without undead-inverting splash healing; no revival, no positive-effect removal, no activation when nobody needs help. |
| Cosmic Nova | One expanding 12-block wave over 30 ticks, 24-second cooldown. Requires at least two valid enemies. Up to 12 distinct hostiles receive 16 HP once each; eligible mobs receive a 20-tick stun with 60-tick reapplication recovery. Early bounded pull respects collision, terrain safety, knockback resistance and boss/control immunity. |
| Primal Domain | Exactly 36-block radius, 300 ticks/15 seconds and 2,400-tick/120-second cooldown. Requires four hostiles, or two hostiles while the owner is at/below 35% HP. Anchored to the defensive position. Two 12-HP waves at ticks 120/180 and one 20-HP final wave at tick 240, each capped at 16 eligible targets. Temporary Slowness I/Weakness I, safe early pull, shared restoration, 20% protection replacing the passive 10%, and a nonstacking 15% allied-Phantom damage bonus against valid threats in the domain. No damage every tick, no permanent buffs, no terrain destruction. |

Cooldown Discipline can reduce eligible normal cooldown slots, but never the ultimate.

## Cosmic presentation

Fifteen species-specific native VFX effects use the existing framework: constellation cues, flanking protective wings, star ribbons, three-hit crescents, recovery stars, Nova and a five-stage domain.

1. Awakening: a stellar sigil and floating original-world fragment form.
2. Convergence: opposing cosmic rings and runic ruins appear.
3. Reshaping: bounded rune pillars and descending starlight accompany the two waves.
4. Prime Manifestation: projected cosmic wings and the final creation burst.
5. Result: ruins, rings and light contract and fade.

Battlefield reshaping is visual only. There are no block changes, forced camera moves, clocks, portals, extra roster accessories or unbounded particles. Native mesh generation peaked at 1,128 vertices in the checked samples, below the existing 4,096 cinematic/1,024 ordinary per-effect budgets. A small-budget fallback and the shared global budget remain intact.

The revised saved-template recolor follows the updated appearance reference: weathered stone and dark-charcoal wing panels, bone-white wing/head plating, restrained pale-gold trim and sky-blue runes/eyes. The old violet palette is removed from her body skins. Adult and baby base textures preserve the template alpha; matching emissive layers illuminate only the eyes and selected markings. The renderer still selects NO_ACCESSORIES and the shared model; scale changes apply to Primordial only. The reference sheet's different statistics, abilities, taming item, horns and halo are not part of this texture-only request.

## Natural encounters

- Main habitat: End Highlands, any time.
- Rare Overworld habitats: mountain-tag biomes at Y100+, or genuinely underground Dripstone Caves, at night (13,000 inclusive to 23,000 exclusive).
- Native spawn weight three, group one; no Nether registration. The species remains Legendary; only its explicit spawn weight increases, not the shared Legendary rarity default.
- Existing custom-spawner infrastructure supplies encounters independently of crowded passive-animal caps. Bounded player rotation, 200-tick/ten-second checks, 32 placement attempts, a local wild singleton cap within 160 blocks and a ten-minute successful-player cooldown keep encounters controlled. The former extra Overworld and cave random vetoes are removed; biome/time restrictions remain unchanged. The later weight increase affects the ordinary weighted natural-spawn pool, not this helper's timers or cap, and does not guarantee three times as many total encounters.
- Full-body clearance, safe ground, fluids/hazards, player distance, world border, mob-spawning gamerule and already-loaded chunks are checked. No End-void spawning, unsafe structure placement or forced chunk loading.
- Placement searches 32–48 blocks away and within 32 vertical blocks of the observer, requires a loaded unobstructed line of sight and checks the actual body volume plus a small clearance margin. Safe ground may lie one to six blocks below the hovering body, rather than exactly two. Underground searches alternate around the observer's elevation across a 25-block vertical range, allowing real low-ceiling caves instead of demanding a three-block-tall expanded body box.
- For in-game searches, use Creative or Survival rather than Spectator, stay near the terrain and keep the world unpaused. The first eligible check occurs after 40 active ticks; subsequent checks are ten seconds apart. Existing nearby wild Primordials suppress duplicates; successful encounters keep the ten-minute cooldown. A check is not a guaranteed spawn if the loaded terrain, visibility or habitat is invalid.

## File inventory

New production Java sources (relative to `src/main/java/net/ronm19/phantomism/`):

- `entity/PrimordialPhantom.java`
- `entity/PrimordialStar.java`
- `entity/ai/PrimordialSupport.java`
- `entity/ai/PrimordialInsight.java`
- `entity/spawn/PrimordialEncounterSpawner.java`
- `vfx/PrimordialVfx.java`
- `client/vfx/PrimordialEffects.java`
- `client/renderer/PrimordialPhantomRenderer.java`
- `datagen/ModPrimordialAnimationProvider.java`

Twenty-four existing integration files receive narrow additions for configuration, discovery/Legendary tier, registration, attributes, placement, tags, creative entries, rendering/network routing, English names and VFX dispatch/bounds/budgets. No existing entity/AI/renderer/model source changes.

New resources: ten PNGs (adult/baby/base/emissive, egg, Heart item, four VFX textures), Heart recipe, ore/cleanse tags, and ten generated animation/discovery/item/model/loot/spawn resources. Thirty-two new source/resource files in total. The exact changed/added inventory and hashes are saved in the task's private `primordial-implementation/change-manifest.json` and `deployed-hashes.json`.

Required adult texture path: `src/main/resources/assets/phantomism/textures/entity/primordial_phantom.png`. Matching baby/emissive files use the existing species naming convention. These are recolored temporary integration skins, not a replacement model or final concept-art geometry.

## Verification and build

- All 265 actual production Java sources compile using the installed Java 25 runtime and the project's exact native NeoForge/GeckoLib dependencies.
- Actual NeoForge client data generation succeeds: 295 generated resources, all nine new animation clips baked, all fifteen VFX definitions checked, and 1,621,411 Primordial native geometry/UV/material/budget assertions passed.
- Actual NeoForge server checks pass: 409 Primordial gameplay assertions and 29 native spawn/placement assertions. Coverage includes real recipe crafting, guaranteed tame, breeding, live Brain pursuit between cooldowns, real primary/child collisions, mounted cooldown history, native damage mitigation, ordinary/friendly/boss projectile filtering, healing/cleansing, finite stun, exact ultimate timing/finite damage waves, safe shifts, local companion duels and persistence.
- Previous Ancient, Angel, Chrono, Demon, Omen, Reaper, Rift, Sentinel and Spectral runtime/spawn suites pass unchanged in the expanded run.
- Shared regression suites pass: existing riding-speed and projectile-range checks, 1,148 native dimension-travel assertions including Primordial cooldown retention, and 2,664 server VFX assertions across all 33 themes and nearby multiplayer packet delivery.
- Both native server runs finish with all required game tests passing and no error-level log entries.
- Full offline Gradle `build` is attempted but fails before tasks with the existing environment error `java.io.IOException: Unable to establish loopback connection`. No new packaged JAR is claimed. Tested production-only developer classes and resources are refreshed; private test classes are not installed into the mod.

## Regression audit and manual checks

Hash comparison confirms all 234 existing PNGs, seven shared model files, 42 existing renderer sources and 105 existing entity/AI/spawn sources are unchanged. No global horn/accessory defaults, geometry or other-species skins are changed. Chrono/Rift effects pass their native client and gameplay checks.

Restart the development client before testing. Live GPU appearance/brightness, camera readability while riding, real-world rarity/travel time, survival balance and a real multi-client network session still require in-game verification. Offline native mesh previews and captured-server packet assertions are not substitutes for those manual checks.

### Spawn reliability follow-up

Only `entity/spawn/PrimordialEncounterSpawner.java` changes in this follow-up; combat, appearance, native weight one, habitats, time rules and the local singleton cap remain unchanged. The tested developer encounter class is refreshed; restart the client to load it. No new packaged JAR is produced.

All 265 production Java sources compile. A focused actual NeoForge run passes 132 new encounter-reliability assertions, all 409 existing Primordial gameplay assertions and all 29 existing habitat/spawn assertions, then finishes its required game test with no error-level log entries. New coverage includes actual registered dispatch, twelve mountain seeds, twelve End seeds, twelve three-block-high deep-cave seeds with native lighting initialized and propagated, visible placement, safe hovering support, no void placement, cooldown expiry in both dimensions, Spectator/gamerule exclusions and failed-placement cooldown safety. The former expanded collision box is demonstrated to reject the same cave that the real body safely fits.

An attempted combined run also encountered a failure in the unchanged probabilistic Ancient underground-spawn fixture. No unrelated Ancient code was altered; the complete combined suite is not claimed as passing again in this follow-up. The isolated Primordial run and source/resource hash comparison provide the scoped verification. Logs and the exact follow-up change manifest are in the task's private `primordial-spawn-reliability/` folder.

### Spawn weight tuning

Primordial's explicit ordinary natural-spawn weight increases from one to three, as requested. The data-generator source and shipped biome modifier agree. The Legendary tier/default, all other species' weights, group size one, allowed habitats/times, encounter-helper timers and safety limits remain unchanged. All 265 production sources compile, native generation reproduces all 295 generated JSON resources, and the focused native server passes 29 habitat/spawning assertions including the updated weight-three expectation. Only the generator class and matching developer resource are refreshed; no new packaged JAR is produced. Restart the client/world to apply the new biome-spawn data.

### Stone-and-gold texture revision

Five existing Primordial-only PNGs are replaced: adult/baby body textures, adult/baby emissive layers and the matching spawn egg. Body/glow textures remain 64x64 and the egg remains 16x16. Minecraft's actual NativeImage loader successfully decodes all five candidates. Base alpha masks and UV layout are preserved; emissive pixels stay inside the base silhouette and cover approximately 10% of occupied texture pixels. Adult three-quarter, top, underside and baby shared-model previews are visually checked before installation.

Only those five source textures and their developer-resource copies change. All other PNGs, every Java source, shared models, accessory toggles, VFX, gameplay values, crafting and weight-three spawning are retained byte-for-byte. Production originals are backed up in the task's private `primordial-stone-gold-reskin/before/` folder; its manifest records the full resource/source audit. This uses the previously approved deterministic template-recolor workflow, without image generation. Reload resources or restart the client to see her new palette. Live GPU appearance still needs an in-game look; the shared-UV preview is not an in-game screenshot.
