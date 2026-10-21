# Dark Phantom #35 — The Living Darkness

Male Apex companion. Uses the existing shared adult/baby Phantom model, normal silhouette, and an explicit `NO_ACCESSORIES` renderer. Attributes: 96 HP, 12 attack, 14 armor, 4 toughness, 55% knockback resistance, 56 awareness, .475 flying-speed attribute (9.5 blocks/sec target tuning), 30-tick melee interval, and normal scale.

## Companion and habitat integration

- Registered entity `phantomism:dark`, projectile `phantomism:darkness_projectile`, spawn egg, creative entry, discovery advancement, attributes, renderer, spawn placement, biome modifier, entity/item tags, loot tables, animations, mounted payload and English translations.
- Ordinary natural spawning: Overworld Dark Forest, clock ticks 13000–22999, weight 6, groups 1–2. Requires loaded, dry, clear space above a valid spawning surface; disabled in Peaceful.
- A species-specific encounter helper supplements the ordinary creature pool, following the existing Chrono/Void spawning architecture. Its initial check is after 40 eligible ticks, then every 200 ticks, with a five-minute recovery after a successful encounter. It supports safe flight space above the forest canopy as well as open ground, caps nearby wild Dark Phantoms at two, checks at most four players per scan, and never loads chunks. This prevents livestock saturation and dense foliage from making Dark practically undiscoverable. It respects mob-spawning rules, night, Dark Forest, player distance, dimension, difficulty, hazards and spectator exclusion. Restart the client/server to register the new helper; a resource reload alone does not load Java changes.
- Wild players receive a three-second warning while remaining within the defended area. Creative/spectator players are excluded; retreating outside the area clears the warning. Direct aggression can justify immediate retaliation.
- Obsidian taming: 75% first valid attempt, then guaranteed for the same player after a failure. Consumes one obsidian per survival attempt. Invalid interactions reset progress; another player cannot inherit the guarantee. A failure expires after five minutes and persists with its player identity through ordinary saving.
- Inherits Staff commands, owner persistence, ordinary breeding and same-species babies, adult riding, mounted controls and family protection. Stay, HOME and noncombat work orders cancel active casts and prevent autonomous powers.
- `G` uses the shared ranged control; Ctrl uses the registered, rebindable Dark melee control, selecting Rend nearby or Rush farther away.

## All ten abilities

| Ability | Gameplay and initial tuning |
| --- | --- |
| Darkness Immunity | Rejects Darkness and Blindness effects on Dark himself. Does not grant general magic, poison or boss immunity. His abilities apply no blindness overlay to allies. |
| Predator in the Dark | 10% outgoing attack bonus at local light 7 or lower, or outdoors at night. Does not stack permanent attribute modifiers. |
| Shadow Pressure | Integer 0–3. Every two successful offensive actions build one level, with a 12-tick gain gate; one sweep counts once. A successful Blackout adds one level. Starts losing one level after five seconds outside combat, then every three seconds, including while ordered to Stay. Saves the bounded resource; client synchronization is one byte. |
| Darkness Projectile | Actual server collision projectile: 12 HP baseline, 42-block absolute travel cap, 200-tick AI cooldown and six-tick mounted cooldown. Solid terrain stops it; allies cannot be hit. Range and mounted cooldown follow the user's approved settings instead of the brief's 28 blocks / 5 seconds. |
| Dark Rend | Six-tick attack windup, 100-tick special cooldown, controlled forward area with up to eight enemies and one hit each. Base multiplier 1.2 + .08 per Pressure level (14.4–17.28 HP before other bonuses and defenses). Ordinary bites remain available between special cooldowns. |
| Darkness Rush | Physical collision-checked charge lasting at most 18 ticks, 200-tick cooldown and exactly one 1.7x impact on the selected target (20.4 HP baseline). Checks the swept mount and passenger volumes. A blocked initial path spends no cooldown. |
| Blackout | Radius 12, up to 16 valid enemies, 320-tick cooldown and 40-tick disruption. Ordinary enemies deal 20% less damage and newly launched supported arrows/small fireballs receive a bounded aim deviation. Existing targets and Brain AI remain intact. Boss-resistant targets receive only 5% offense reduction and no aim deviation. |
| Bubble of Darkness | Reactive four-second, six-block zone; 500-tick cooldown. Gives eligible allies 15% mitigation against valid hostile attacks and briefly disrupts enemies in the zone. Multiple bubbles select the strongest protection rather than multiplying it. |
| Shield of Darkness | Reactive self-defense for meaningful hostile hits of at least 4 HP. Four-second lifetime, 600-tick cooldown, 35% per-hit reduction and finite 12-HP absorption budget. Bypassing damage is respected. Bubble and shield use the stronger reduction per hit, without stacking. |
| DARK HOUR | Exact 1800-tick cooldown, 200-tick duration and 24-block radius. Requires a real current combat target and at least three eligible enemies. Marks up to 24, applies bounded disruption, consumes current Pressure for the cast's fixed damage budget, and grants 10% offense against marked enemies while active. Launches at most twelve descending blades, at twelve-tick intervals during the Hunt, with at most four in flight. Each blade deals 6–9 baseline damage depending on consumed Pressure. Ends with exactly one 16–22 HP final shockwave, removes ultimate disruption and discards remaining blades. |

Values not fixed in the source brief are provisional balance choices. Darkness and marked-target bonuses can combine to a maximum 1.21 multiplier; defenses, projectile immunity and boss rules still apply. Crowd scans, affected-target counts, blade counts and damage applications are bounded. No world-time, weather, terrain destruction, permanent AI disabling or full-screen darkening is used.

## VFX, resources and sound

`DarkVfx`, `DarkEffects` and four Dark-only VFX textures extend the current NeoForge-native cinematic system. Effects include intersecting claw ribbons, compressed Rush trails, fractured Blackout sigils, sparse spherical bubble arcs, assembling angular shield plates, descending blade meshes and a three-stage Dark Hour with jagged concentric ground sigils, a suspended fractured emblem, predatory ribbons and a layered final shockwave.

The existing particle quality settings, 80-block culling, 64-effect group cap, 8192-vertex group budget, per-effect budgets, dimension checks, session cancellation and attachment cleanup remain in force. No global model or accessory defaults changed. Adult/baby textures were recolored from the saved shared templates, retain their alpha/UV layout and have separate emissive eye/marking maps:

- `assets/phantomism/textures/entity/dark_phantom.png`
- `assets/phantomism/textures/entity/dark_phantom_eyes.png`
- `assets/phantomism/textures/entity/dark_phantom_baby.png`
- `assets/phantomism/textures/entity/dark_phantom_baby_eyes.png`

GeckoLib has dedicated bolt, Rend, Rush, Blackout, bubble, shield, charging, hunting and final attack clips using existing bones. Sound cues reuse registered vanilla Phantom and Wither sounds; no unregistered external audio references or new library dependency was introduced.

## Implementation files

New Java files: `entity/DarkPhantom.java`, `entity/DarkProjectile.java`, `entity/ai/DarkControl.java`, `entity/spawn/DarkEncounterSpawner.java`, `vfx/DarkVfx.java`, `client/vfx/DarkEffects.java`, `client/renderer/DarkPhantomRenderer.java`, and `datagen/ModDarkAnimationProvider.java`.

Existing files receive focused registration, configuration, tags, translations, mounted networking and native VFX dispatch additions. Nine new PNGs provide adult/baby base and emissive textures, a spawn egg and four VFX materials; generated resources provide animations, spawning, discovery, egg models, loot tables and expanded roster tags. The detailed file manifest and native test logs are under the private `work/phantomism-private/dark-implementation` workspace.

## Verification

- All production Java sources compile with the installed Java 25 compiler and actual Minecraft/NeoForge/GeckoLib dependencies.
- 268 native gameplay assertions pass, covering all ten abilities, real collision/damage, taming, mounting, Pressure bounds, timed hit windows, physical Rush, hostile projectile aim disruption, temporary defense/disruption, ultimate blade limits, cleanup, persistence, warning behavior, autonomous live Brain pursuit, Staff orders and foreign-pet duel isolation.
- 20 native spawning checks pass, covering the actual Dark Forest predicate at day/night boundaries, dimensions, terrain collision/liquid rejection and generated weight/group size.
- 24 additional actual-encounter checks pass after the reported spawning failure. The registered server helper creates a real group in a dense-canopy Dark Forest with twelve nearby cows, waits until night, avoids unsafe/unloaded positions, respects spectator/underground/gamerule/biome restrictions and enforces recovery and population caps. The earlier 20 predicate checks alone did not establish encounter reliability.
- 1,634,225 native rendering/resource assertions pass: PNG decoding, actual GeckoLib animation baking, VFX registration, finite vertices, valid UVs and low-budget geometry fallback. Measured maximum is 872 generated vertices for one Dark effect; this is a geometry budget result, not an FPS measurement.
- Data generation completes successfully and creates 311 JSON resources. Existing generated assets remain identical except the two expanded roster tags.
- Hash audit confirms all 253 pre-existing PNG textures, seven shared model files and 115 existing entity/AI Java files are unchanged.
- The repository Gradle build was attempted with Java 25 and fails before task execution with `java.io.IOException: Unable to establish loopback connection` (`UnixDomainSockets.connect`: invalid argument). No successful Gradle build or new packaged JAR is claimed.

Eleven existing recent-species gameplay suites pass in an isolated group. Shared Blood/Echo/Skeleton/Vampire/Gravity/Wither, mounted-speed, projectile-range, VFX-networking and dimension-travel regressions also pass. The VFX suite covers all 35 species; the inherited dimension fixture covers its existing 23-species matrix. An initial combined regression harness hit an older randomized Chrono encounter assertion after multiple world-mutating spawn fixtures; unrelated Chrono spawning code was not changed. The final private report records the successful isolated runs and development-output refresh.

Still required: real graphical Dark gameplay review, survival balance, live multiplayer spectators/latency, and FPS testing with several simultaneous Dark Hours. Native engine tests and mesh checks do not replace those observations. Adult/baby texture previews were inspected; no on-screen Dark combat session is claimed.
