# Ancient Phantom #31 — implementation report

Implemented against the installed Minecraft 26.3 / NeoForge 26.3.0.64-beta / Java 25 project. All seven reference abilities are implemented server-side. Values not fixed in the reference are provisional, not gameplay-proven balance.

## Companion and appearance

- Elite male companion: 82 HP, 9 attack, 12 armor, 6 toughness, 50% knockback resistance, 64-block awareness, 8 blocks/sec configured flight, 38-tick normal melee interval and 1.1 species scale.
- Existing shared model, breeding, owner persistence, Staff commands, riding, rebindable controls and flock infrastructure remain in use. No new model or accessory bones.
- Adult and baby saved templates recolored in weathered sandstone, dark stone and cyan rune markings, with matching emissive eyes and spawn egg. No image generation. Optional accessories are disabled only for Ancient.
- Primary adult texture: `src/main/resources/assets/phantomism/textures/entity/ancient_phantom.png`.
- First valid Ancient Relic interaction guarantees taming and consumes one item outside Creative mode. Recipe: one Echo Shard in the center of eight stone bricks. Relic and egg have creative-tab entries and item models.

## Abilities

| Ability | Status and initial tuning |
| --- | --- |
| Ancient Mark | One dangerous, valid enemy marked for 12 seconds, up to 64 blocks with visibility. Ancient's runic abilities gain 15% damage against that enemy. Nearby idle same-owner followers can focus it without overriding their orders, work, existing targets or duel boundaries. |
| Ruin Strike | 1.8x attack = 16.2 HP before defenses/mark bonus, real melee contact, controlled knockback and 10-second cooldown. 25% fragment chance: up to four additional valid enemies within 2 blocks take 3 HP. Boss movement immunity is respected. |
| Stone Projectile | Real server-authoritative shard: 42-block total travel, 10 HP impact damage within 3 blocks, up to 12 valid enemies and 2 seconds Slowness I where permitted. AI cooldown 12 seconds; owner-mounted G cooldown 6 ticks / 0.3 seconds. Dismounting does not refund the AI cooldown. |
| Ruin Shield | 20% incoming hostile-damage mitigation for Ancient, owner and same-owner Phantoms within 16 blocks and visibility. Duration 12 seconds, cooldown 25 seconds. Requires combat; same-species fields do not multiply mitigation. |
| Earthquake Dive | Collision-checked approach to actual solid ground; 12 HP shockwave within 7 blocks, up to 12 valid enemies, up to 1 second actual mob stun. Cooldown 16 seconds. Stun recovery is 3 seconds and excludes bosses/players. No NoAI flag changes, teleporting or block destruction. |
| Ancient Sanctuary | Anchored 18-block field, 15 seconds, 40-second cooldown. Eligible allies receive 20% mitigation and direct healing capped at 1 HP per recipient per 2 seconds across overlapping Ancients. Heals undead Phantoms correctly; valid enemies receive Slowness I. Requires an injured ally. |
| Calamity: Forgotten Land | 35-block radius, 14 seconds, 120-second cooldown. Requires at least four visible valid hostiles. Exactly three damage waves, at ticks 100/160/220; 20 HP per wave before mark bonus, up to 12 valid enemies per wave, short stun with recovery. Eligible allies receive non-stacking 20% mitigation and same-owner allied Phantoms gain 10% damage within the field. |

Normal melee still pursues and attacks between special cooldowns. Ancient does not blindly attack peaceful mobs or unprovoking players. Wild Ancients are neutral unless attacked; owned Ancients defend legitimate threats and honor assigned targets.

Ctrl uses the existing mounted network route: Ruin Strike at melee contact, otherwise Earthquake Dive against a valid aimed threat. Mounted Dive does not forcibly steer the rider; approach the ground to complete the shockwave. An unfinished dive times out safely. G uses the existing shared secondary/projectile route.

## Spawning

- Overworld only, night from clock ticks 13000 through 22999, rare weight 3, groups 1–2.
- Mountain biome tag, windswept hills/forest/gravelly hills, and rare deep lush/dripstone cave encounters.
- Ruins mapped to actual already-loaded Trail Ruins, Desert Pyramids, Jungle Temples and Ancient Cities, with bounded 64-block surroundings/vertical checks. No custom structure is fabricated and no search forces chunks to load.
- Existing custom-spawner infrastructure supplies capped encounters even when ordinary creature slots are saturated. At most two wild Ancients in a 160-block horizontal vicinity; successful encounters have a four-minute player cooldown. Deep-cave attempts have an additional 1-in-8 roll.
- Mob-spawning gamerule, player exclusion distance, world border, build height, fluids, full-body collision and loaded space are checked. No village/structure blocks are modified.

## VFX and safety

Ancient-only native mesh forms and four new textures provide carved sigils, floating runic stone pillars, actual projectile trails, sweeping strikes, shards, ground shockwaves and the staged Forgotten Land sequence: awakening sigil, rising pillars, falling shards, three synchronized impact waves, contracting/fading ruins. These are visual meshes, not placed terrain or accessory variants.

Native rendering tests cover all quality levels, exact effect lifetimes, finite positions/UVs and low-budget fallbacks. Peak Ancient mesh: 848 vertices, below the existing cinematic budget. The 35-block packet exception is restricted to Forgotten Land and its conclusion; unrelated packet limits and old VFX definitions are unchanged.

Server targeting revalidates living enemies, visibility, range, ownership, protected passengers, invulnerability, loaded flight segments and command state. Death, unload, owner loss, dimensions and cancellation release transient effects/stuns; cooldowns persist without replaying active abilities. Ordered foreign-pet duels remain local and do not recruit the flock. Existing shared Phantom-versus-Phantom target exclusion is intentionally unchanged.

## Files

Eight new Java sources: `AncientPhantom`, `AncientStone`, `AncientControl`, `AncientEncounterSpawner`, `AncientPhantomRenderer`, `AncientEffects`, `AncientVfx`, `ModAncientAnimationProvider` in their matching existing entity/AI/spawn/renderer/VFX/datagen packages.

Ten new PNGs: adult/baby base and emissive skins, egg, Relic, and four Ancient-only VFX textures. Eleven new resource files: animation, two client-item definitions, two item models, discovery advancement, two entity loot tables, biome modifier, biome tag and Relic recipe.

Twenty-three existing integration files updated: Config, Phantomism, PhantomismClient, PhantomDiscovery, PhantomismContent, ModSpawnPlacements, ModTags, PhantomismNetwork, PhantomVfxTheme, VfxPacket, VfxRegistry, VfxMesh, VfxGroup, VfxManager, six existing datagen registration/providers, English translations and the two generated roster tags. No existing species entity, AI, renderer or model source was edited.

## Verification and build

- All 247 production Java sources compile against the actual installed libraries; only existing deprecated-API notes remain.
- Native Minecraft server: 345 Ancient gameplay assertions, including real recipe assembly, tame/breed, actual collisions/damage/healing, separate mounted recovery, status/field expiry, immunity, commands, local duels and live Brain pursuit between cooldowns.
- Native spawning: 42 checks, including registered encounters in mountain/windswept/loaded-ruin habitats, underground encounters, night boundaries, groups, gamerule and unsafe-space rejection.
- Native client/data: 1,840,987 Ancient mesh/UV/animation/budget assertions. All previous specialist client suites, including Chrono, Rift and Angel halo checks, pass. 277 resources generated and validated.
- Prior specialist gameplay/spawn suites pass. Shared regressions pass: 208 mounted-speed assertions, 384 existing-projectile-range assertions, 1,078 dimension-travel assertions and 2,504 shared VFX/network assertions. The roster loops include Ancient; the dimension suite's printed “twenty-three species” wording is an old label, not its actual loop bound.
- Hash audit: all 213 existing PNGs, seven existing model files, 40 renderer sources and 97 existing entity/AI sources are unchanged. No horns enabled globally, no old textures replaced, no geometry variants generated.
- Full offline Gradle build attempted; packaging fails before project tasks with `java.io.IOException: Unable to establish loopback connection`. This is the existing local Gradle environment issue, not a passing packaged build. No new JAR is claimed.
- Tested classes and source/generated resources are refreshed in the development output. Restart the development client to load them.

## In-game checks still required

Live GPU appearance (especially shader-pack transparency), sustained performance with several simultaneous fields, real multiplayer latency/controls, mounted Dive handling, survival encounter frequency and combat/healing balance require gameplay testing. Headless native tests do not replace those checks. Quick spawn command: `/summon phantomism:ancient`.
