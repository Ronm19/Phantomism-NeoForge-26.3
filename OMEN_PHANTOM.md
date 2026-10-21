# Omen Phantom #30 — The Harbinger Queen

Female Elite anti-raider commander, implemented in the existing Minecraft 26.3 / NeoForge 26.3.0.64-beta / Java 25 architecture. Uses the existing shared Phantom models and staff, ownership, breeding, riding, family safety and native VFX systems.

## Profile and encounters

76 HP; 8 attack; 10 armor; 4 toughness; 40% knockback resistance; 8.5-block/sec flight profile; 56-block awareness; 34-tick ordinary melee interval; scale 1.0. These remain initial balance values, not proven gameplay balance.

Overworld, any time: loaded Pillager Outpost surroundings, Dark Forest, Savanna and Savanna Plateau. Fallback biome registration has weight 6 and group size 1. The outpost encounter helper checks actual native structure starts in already loaded chunks, within 80 blocks of their native bounding boxes; it never locates or generates distant chunks. It excludes solid/liquid/unsafe spaces, world-border violations and positions closer than 24 blocks to a player. The helper limits wild Omen encounters to one within 160 blocks, checks up to four players per attempt and waits two minutes per player after success. Normal biome spawning remains registered alongside the helper.

Wild Omen is neutral to unprovoking players and village inhabitants, but actively opposes raiders. Tame with one **Ominous Bottle**, guaranteed on the first valid attempt; this does not drink the bottle or apply Bad Omen/Raid Omen to the player. Spawn egg: `phantomism:omen_phantom_spawn_egg`. Entity: `phantomism:omen`.

## Ability status

All nine abilities are functional and server-authoritative:

1. **Raider Knowledge:** recognizes native raider/illager tags, including Pillagers, Vindicators, Evokers, Ravagers and Illusioners. Prioritizes marked targets, raiders, dangerous casters and durable enemies. Protected allies, unprovoking players, foreign-owned companions and village inhabitants are not automatic victims.
2. **Raid Command:** sends up to eight nearby same-owner idle adult followers against a valid marked raider. Coordination is limited to 28 blocks, respects each recipient's existing targeting/range/suppression rules and never replaces an existing combat target, Stay, HOME, work, guard or mounted control. Authorized foreign-pet duels do not spread to observers.
3. **Harbinger:** bounded 56-block threat detection drives target priority, marking and coordination. Nearby raiders trigger occasional emerald awareness pulses, supplementing Omen's emissive eyes and rune markings.
4. **Omen Bolt:** real collision-based piercing projectile; 9 HP before defenses; up to three distinct eligible enemies per shot. Brief emerald target crests accompany successful hits. Your established adjustments apply: **42-block total travel range and 0.3-second mounted recovery**, retaining **10-second AI recovery**. Terrain and unloaded chunks stop the shot; there are no destructive explosions or projectile-immunity bypasses.
5. **Condemned Dive:** 1.6x attack, initially **12.8 HP**, with a 12-second cooldown and 12-block acquisition range. One collision hit per activation, collision-safe movement, and no forced rider steering. Ordinary melee and shared Brain pursuit continue while special attacks recharge.
6. **Ominous Mark:** one active target for 12 seconds, 18-second cooldown. Replacement, death, expiry or invalidation clears the old target and its client crest. Marking does not globally change teams or force every companion into combat.
7. **Dark Standard:** 12 seconds, 20-block radius, 30-second cooldown. Same-owner allied Phantoms gain **15% damage against eligible raiders** within the defensive position. Multiple Standards do not multiply the bonus. The caster does not buff herself through her own Standard. A hostile encounter and an allied Phantom are required, preventing empty buff spam.
8. **Omen Shield:** reactive finite **12-HP absorption reservoir**, lasting at most three seconds, with a 25-second cooldown. Applies to real incoming hostile damage after ordinary defenses, honors bypass tags and does not refresh itself after being depleted. No permanent invulnerability or recurring absorption refill.
9. **Calamity:** 90-second cooldown, 12-second duration, **28-block radius**. Three actual judgment waves at 3, 5.5 and 8 seconds; each affects at most eight valid visible targets once. Each strike deals **5 HP**, or **6 HP to raiders**, before defenses. Brief Slowness I applies only to eligible non-boss targets; there is no terrain destruction. The ultimate maintains one priority mark and provides the same non-stacking allied anti-raider support. The final visual contraction adds no extra hidden damage wave.

Conservative provisional ultimate trigger: at least two valid raiders or four other legitimate hostile enemies within 28 blocks. No valid crowd means no activation or cooldown consumption. The ultimate is tied to a loaded position and cancels if its source dies, unloads, changes dimension, receives a stopping command or moves more than 56 blocks away. Cooldowns persist without replaying active fields or projectiles after reload.

## Appearance and VFX

Adult/baby skins, emissive overlays and spawn egg are recolored from the saved templates: obsidian/charcoal, deep green, emerald runes and mint highlights. The existing UV layout and base alpha silhouette are preserved. No image generation, new model, horns, spikes, halo, accessory variants or shared accessory-default changes.

Calamity's native sequence: forming overhead queen's sigil; condemnation banners and energy links; three synchronized expanding judgment waves; tactical command crests; final contraction/fade. Separate rune-mark, shield, projectile, dive, banner and impact effects use Omen-specific artwork, not Chrono clocks or Rift portals. The overhead sigil faces the viewer for readability, while its ground reflection and banners remain spatially anchored.

Existing multiplayer delivery, source/session cancellation and quality budgets remain intact: 64 events per server tick, 64 active client effects and 8192 submitted vertices per frame. Omen's largest tested single submission is 632 vertices. The packet's 28-block radius allowance applies only to Omen's ultimate/conclusion; other species' bounds remain unchanged. Invalid optional Omen VFX vectors are rejected before packet creation rather than crashing gameplay.

## Files

New Java files under `src/main/java/net/ronm19/phantomism`:

- `entity/OmenPhantom.java`, `entity/OmenBolt.java`
- `entity/ai/OmenTactics.java`, `entity/spawn/OmenEncounterSpawner.java`
- `client/renderer/OmenPhantomRenderer.java`, `client/vfx/OminousVfx.java`
- `vfx/OmenVfx.java`, `datagen/ModOmenAnimationProvider.java`

Integration additions: Config, entity/attribute/egg/discovery/spawn registration; biome/entity/item tags and item model/data providers; English translations; species-themed shared VFX dispatch; packet radius, effect budget and mark-close handling; existing mounted input/network routing. No existing entity, shared Brain, riding implementation, shared model or species renderer is modified.

Nine Omen-only PNGs: adult/baby textures and `_eyes` overlays in `textures/entity`, `textures/item/omen_phantom_spawn_egg.png`, and `textures/vfx/omen_sigil.png`, `omen_banner.png`, `omen_crest.png`, `omen_trail.png`. Generated resources include the real GeckoLib animation set, spawn data, loot, discovery and egg assets. A private change manifest records every touched path.

## Verification

- All **239 production Java sources** compile against the installed native libraries; only existing deprecation warnings remain.
- **301 Omen gameplay checks pass**, including all abilities, actual piercing collision, 12.8-HP Dive, finite native shield, non-stacking Standard, exactly three bounded judgment waves, live Brain pursuit, commands, foreign-pet duel isolation and saved cooldowns.
- **36 native spawning checks pass**, including actual registered day/night callbacks in both fallback biomes and native outpost-start surroundings, singleton behavior, gamerule, safe clearance, loaded chunks and dimensions.
- **883,114 Omen client geometry/material/UV/animation/budget checks pass** across all quality levels and cinematic stages. Actual submitted geometry and textures are also rendered into an offline preview; that is not an in-game GPU screenshot.
- Existing Angel, Chrono, Demon, Rift, Sentinel and Spectral gameplay/client suites pass, including Angel's halo and teleport-trail/combat fixes.
- Expanded-roster regressions pass: **202 mounted-flight checks**, **1043 native dimension-travel checks** including Omen's projectile/ultimate cooldown history, and **2424 shared VFX/network checks** across all 30 themes. Blood, Echo, Gravity, Skeleton, Vampire and Wither suites also pass.
- Native data generation produces 267 resources; a repeat writes zero changed resources.
- The normal offline Gradle build still fails before project tasks with `java.io.IOException: Unable to establish loopback connection`. Developer classes/resources are refreshed after verification; **no packaged JAR is claimed**.
- Hash regression verifies every previous texture, shared model, accessory toggle, species renderer and entity/AI source remains unchanged. Omen adds no global horns or model geometry.

Private tests, logs, source hashes, previews and file manifest: `work/phantomism-private/omen-implementation` in the chat workspace. Older private fixtures' expected roster totals are updated to 30; peaceful Omen receives a real provocation in the shared awareness test, rather than weakening her wild-player safety rules.

## Live checks still needed

Restart the development client. Find Omen near an explored outpost or in Dark Forest/Savanna, tame with an Ominous Bottle, test staff commands and adult riding, G's 0.3-second Bolt and Ctrl's Dive. Fight a genuine raider group with allied Phantoms; confirm single-target coordination, banner support, shield depletion, three Calamity waves and protected allies. Compare with ordinary targets while specials recharge. Verify the full overhead sigil, shader/transparency behavior and camera readability in your actual renderer.

Real multiplayer latency, live GPU performance, resource-pack/shader compatibility and gameplay balance still require manual playtesting. Spawn weight, rarity and all mitigation/damage values are initial tuning rather than guaranteed encounter frequency or final balance.
