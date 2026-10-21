# Reaper Phantom #32 — The Harvester of Souls

Implemented against Minecraft 26.3, NeoForge 26.3.0.64-beta and Java 25. All eleven reference abilities are implemented. Unspecified ability values below are provisional balance choices, not proven survival-gameplay results.

## Companion, controls and appearance

- Elite male: 72 HP, 9 attack, 10 armor, 4 toughness, 40% knockback resistance, 48-block awareness, configured flight 9 blocks/sec, 32-tick normal melee interval and normal 1.0 scale. Existing shared mounted cruise behavior is preserved.
- Existing shared Phantom model and animations, per-species renderer, Staff commands, taming, breeding, owner persistence, riding and flock protection remain in use.
- Saved adult/baby templates recolored in charcoal, violet and cyan. Hood shading is painted into the head texture; no separate hood, horns, spikes or other accessory geometry. Optional accessories are disabled for Reaper only. Base texture: `src/main/resources/assets/phantomism/textures/entity/reaper_phantom.png`.
- Wither Skeleton Skull guarantees taming on the first valid attempt; one is consumed outside Creative mode. Vanilla skull appearance is unchanged.
- G: Soul Shard. Ctrl: Mortal Sever against a suitable nearby target, otherwise Soul Cut. Controls use existing rebindable mappings and server-authorized routing. Reaper Passage intentionally refuses to teleport a mounted rider.

## Ability implementation

| Ability | Status and initial tuning |
| --- | --- |
| Death Sense | Bounded visible scans within 48 blocks prioritize wounded enemies approaching defeat and dangerous targets. Up to three subtle wounded-enemy cues every two seconds; no through-wall targeting. |
| Soul Harvest | One Soul Fragment from an eligible hostile enemy Reaper actually helped defeat in the last 10 seconds. Actual server damage establishes credit, not proximity or marking. Maximum three fragments. Living enemies, owners, companions, peaceful bystanders and ordered pet duels cannot provide fragments. Duplicate events cannot re-credit the same victim through the combat ledger. |
| Executioner's Edge | Reaper's attacks gain 25% damage when the victim is strictly below 30% maximum health. Exactly 30% does not qualify. Native armor, invulnerability and projectile rules still apply. |
| Finality | Successful attacks apply 30% native healing reduction for three seconds; Mortal Sever and bell tolls apply 50% for six seconds. Only the strongest applicable Phantomism anti-heal wins, including Blood's Bleeding. Effects do not multiply together. Cleansing, source loss, commands, range and server-time expiry invalidate the source-bound lease. |
| Reaper's Mark | One visible priority target, 10-second duration and 12-second cooldown, within 48 blocks. Last Bell preserves an existing valid mark until that ultimate ends so it cannot expire just before judgment. |
| Soul Cut | Real forward crescent melee, 1.2x attack = 10.8 HP before defenses/execution bonus, up to four valid enemies within four blocks, six-second cooldown. Each target can be hit once per activation; rear targets and protected allies are excluded. |
| Mortal Sever | Heavy contact attack against an enemy at or below 50% health, regenerating, or recently healed. 1.8x attack = 16.2 HP before defenses/execution bonus, 12-second cooldown and strong Finality. A healthy non-regenerating enemy does not waste its cooldown. |
| Reaper Passage | Safe short reposition near a valid visible enemy within 12 blocks, followed by one suitable real melee special. 16-second cooldown. Loaded swept volume, collision, fluids, border and build height are checked. No blind wall crossing, passenger teleport or world alteration. |
| Soul Shard | Real server-authoritative single-hit projectile, 10 HP before defenses/execution bonus, 39-block total travel range, AI cooldown 12 seconds and owner-mounted cooldown six ticks / 0.3 seconds. Dismounting does not refund AI recovery. |
| Death's Bargain | Only after a legitimate nonlethal hostile hit leaves Reaper at or below 25% health: consumes available fragments and heals four HP each, up to 12 HP. Adds three seconds of 20% hostile-damage mitigation, with a 60-second cooldown. Does not cancel lethal damage, resurrect, steal lives or protect unrelated allies. |
| The Last Bell | 24-block radius, 12-second duration, 100-second cooldown. Requires four valid hostiles or two valid enemies below 30% HP. Three tolls at ticks 60/110/160 deal four HP before execution bonus to up to 12 enemies per toll and apply strong Finality. At tick 200, up to ten marked OR below-30%-HP enemies receive an 18 HP finishing strike before execution bonus. Normal server kill/assist rules award eligible souls, capped at three. |

All damage and resource awards are server-authoritative. Ordinary melee/flight pursuit continues between special cooldowns. Mark, shard, melee and passage do not consume their cooldown when activation fails. Ultimate/emergency cooldowns are excluded from Chrono's ordinary cooldown reduction. Saved fragment counts are clamped to 0–3; active fields, old assist credits and status leases are not replayed after loading or dimension copies.

## The Last Bell and other VFX

Reaper-only native VFX include a shaped three-dimensional spectral bell with moving clapper, violet/cyan carved sigils, orbiting souls, curved harvest trails, crescent slashes, safe-passage echoes, impact flashes, reactive soul stabilization and finishing pillars.

The ultimate forms a faint localized shadow canopy and awakening sigil, raises the bell and swirling soul runes, sends three synchronized expanding toll waves and energy arcs, performs judgment, then contracts/fades into lingering runes. Bell sounds play server-side at the three tolls. The canopy is translucent world-space geometry, not a global sky, weather, world-time or camera-setting change.

Existing active-effect and vertex budgets remain in place. Repeated orbiting-soul updates replace their own prior effect, rather than stacking unlimited wisps. Harvest and passage have full directional culling bounds. Effects are caster/session scoped and canceled on relevant commands, source death, unload, invalid owner or dimension change.

## Spawning and temperament

- Rare weight 3, group size one.
- Nether: Soul Sand Valley, any time, below the roof.
- Overworld fallback: Dark Forest, night only (clock ticks 13000 through 22999).
- Neutral toward unprovoking players, villagers, golems and protected companions; hunts legitimate hostile monsters. Neutral mobs such as unprovoked piglins are not automatic prey.
- Existing native biome/placement registration plus a bounded custom encounter helper. At most one wild Reaper in the local 128-block vicinity, with a four-minute successful player encounter cooldown. Mob-spawning gamerule, live nonspectator players, 24-block exclusion, loaded chunks, full-body clearance, safe support and fluids/hazards are checked. No terrain is destroyed or generated for encounters.
- Quick controlled test: `/summon phantomism:reaper`.

## Files and scope

Nine new Java sources: `ReaperPhantom`, `ReaperShard`, `ReaperSouls`, `FinalityEffect`, `ReaperEncounterSpawner`, `ReaperPhantomRenderer`, `ReaperEffects`, `ReaperVfx`, and `ModReaperAnimationProvider` in their corresponding existing packages.

Eleven new PNGs: adult/baby base and emissive skins, spawn egg, Finality icon, and five Reaper-only VFX textures. Eight new generated resources: animation, egg item/model, discovery advancement, entity/projectile loot tables, biome modifier and biome tag. English translations and existing creative/discovery/roster integrations include Reaper.

Twenty-four existing integration files change: Config, Phantomism, PhantomismClient, PhantomDiscovery, PhantomismContent, ModSpawnPlacements, ModTags, PhantomismNetwork, PhantomVfxTheme, six datagen registration/providers, five client VFX integration files, English translations, two generated roster tags, and one shared damage/healing event handler.

The only existing entity/AI-source edit is one expression in `BloodCombatEvents`: the healing factor becomes the minimum of Bleeding's and Finality's factors. Without an active Reaper effect, Blood's behavior is unchanged. This surgical integration prevents unintended multiplicative anti-healing; no broad combat or accessory refactor is included.

## Validation and build

- All 256 production Java sources compile against the installed native libraries; existing deprecated-API notes remain.
- 509 Reaper native gameplay assertions pass: statistics, taming/breeding, actual damage/collision, execution threshold, real kill/assist credit and cap, native healing and strongest-only integration, reactive stabilization, lethal-hit non-resurrection, passage, precise bell timing, saved state, commands, riding and local companion duels.
- 26 Reaper native spawn assertions pass: actual encounters in both dimensions, night boundaries, singleton population, gamerule, collision, unloaded-chunk safety, roof exclusion and exact weight 3.
- Native client/data checks pass across all quality levels and tight geometry budgets, including complete long-trail culling bounds. Existing specialist client/animation suites pass, including Chrono, Rift, Ancient and Angel halo regressions. 285 native resources generate successfully.
- Earlier specialist gameplay suites pass, including Blood and Ancient. Shared regressions pass: 214 mounted-speed assertions, 384 prior-projectile-range assertions, 1,113 dimension-travel assertions and 2,584 VFX/network assertions. Roster loops cover all 32 species; some old human-readable labels in private fixtures still name earlier roster sizes.
- Hash audit preserves all 223 previous PNGs, seven existing model files and 41 renderer sources, plus all 100 other existing entity/AI sources. No other Phantom gains horns/accessories, no shared model geometry changes and no previous texture replacement.
- Full offline Gradle build attempted: packaging remains blocked before project tasks by the existing `java.io.IOException: Unable to establish loopback connection` environment issue. No new packaged JAR is claimed. Tested production classes and main/generated resources are refreshed in the development output; restart the development client to load them.

## Still requires gameplay testing

Live GPU/shader-pack transparency, the bell canopy from different camera angles, real multiplayer latency, close combat while riding, sustained performance with multiple Reapers, survival encounter frequency and provisional combat/healing values require in-game verification. Headless native checks and offline mesh previews do not replace these checks.
