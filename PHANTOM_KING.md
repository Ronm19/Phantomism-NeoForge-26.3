# Phantom King #36 - The Sovereign of the Skies

Male Apex companion, `phantomism:phantom_king`. Implemented for Minecraft 26.3, NeoForge 26.3.0.64-beta and Java 25, using the existing Phantom Brain, Staff, riding, breeding, ownership, GeckoLib and native VFX systems.

## Identity and appearance

144 HP, 14 attack, 22 armor, 8 armor toughness, 85% knockback resistance, 64-block awareness, 9 blocks/second flight tuning and a 32-tick ordinary melee interval.

The native SCALE attribute is 2.0. Dimensions are refreshed at construction, so an adult has a real 2.8 x 1.8-block collision box rather than just a doubled picture. Native scaling also feeds the renderer. The King's seat follows the shared body's back, at 0.44 times native scale above the origin and 0.22 times scale forward along his yaw, behind the crown. At scale 2 this lowers the previous seat by about 0.29 blocks and centers it about 0.44 blocks forward. Native passenger attachment offsets remain in use. Babies retain the existing proportional age scaling and cannot activate combat abilities.

Nine King-only PNGs provide template-derived royal blue, indigo, gold markings, bright blue-white eyes, an egg and four royal VFX materials. The existing shared adult and baby geometry is unchanged. A King-only head render layer adds a compact physical gold crown (56 quads / 224 vertices) to the existing head bone. Optional horns and other unrelated accessories remain disabled for the King; other renderers and accessory defaults are untouched.

## All eleven abilities

| Ability | Server-side implementation and initial tuning |
| --- | --- |
| Royal Authority | Every 20 ticks, coordinates up to 12 eligible nearby adult allied Phantoms. Normal radius 24, Dominion radius 40. Short target leases never change owner orders or species abilities. |
| King's Presence | 24-block aura: +8% allied Phantom damage and 8% eligible crew mitigation. Multiple Kings take the strongest applicable aura, not repeated stacks. |
| Royal Intercept | 9-second cooldown, 20-block ally search, 12-block approaching-attacker check. Safe movement toward an intercept position, a short per-victim reservation, and Royal Guard if available. Protects the owner or allies at/below 35% health; does not override a different explicit owner target or operate while ridden. |
| Royal Momentum | Synchronized integer 0-3; successful hits build at most one point per 20 ticks. Outside active combat, begins decaying after 5 seconds, then one point every 3 seconds, including Stay. |
| Royal Ram | 12-second cooldown, 24-block acquisition, 8-tick windup and up to 18 movement ticks. Physical collision-checked charge; one 28-base hit, +4% per cast-time Momentum, controlled knockback. At Momentum 2+, one permitted log/leaf clearing attempt, maximum 8 blocks. |
| King's Judgment | 20-second cooldown, 32-block cast range; one 6-second mark. Allied Phantom hits gain 10% damage vulnerability (5% for resistant bosses), bounded with Presence to +18%. Temporary focus only for eligible allies. |
| King's Strike | 6-second special cooldown, 7-tick hit window; one melee hit at 1.35x + 0.12x per Momentum. Ordinary 14-base bites remain available on their separate 1.6-second interval. |
| King's Roar | 25-second cooldown, 16-block area, requires at least two enemies. Up to 20 valid enemies receive controlled push and 1.5 seconds of 15% outgoing-damage reduction; resistant bosses get only 5%. No Brain deletion or repeated refresh; 4-second disruption recovery. |
| Royal Command | 20-second cooldown, 32-block priority target. Focus, distinct safe orbit positions and bounded attack-timing credit. Only eligible normal cooldowns receive up to 8 ticks credit, once per ally per 20 seconds across Kings; ultimates and revival cooldowns are excluded. |
| Royal Guard | Meaningful hostile damage of at least 4 HP can trigger 25% mitigation for 4 seconds within 12 blocks, on a 28-second cooldown. Uses the strongest protection, not additive invulnerability. |
| Royal Decree: King's Dominion | Exactly 15 seconds, 40-block radius and 120-second cooldown. Requires a legitimate active target and at least three enemies. Coronation marks up to 32 enemies; Army coordinates actual allies and gives the King +15% offense / 20% self-mitigation; Final Decree deals one 24 + 2 per Momentum base finishing hit and short disruption to still-valid marked enemies. It does not kill by fiat or damage allies. |

Values not fixed by the design are provisional, conservative tuning rather than proven survival balance. Final Decree uses its stated 24-30 base damage; the Army's +15% modifier has expired by the finishing hit. Guard takes precedence over lesser mitigation. The passive Authority may resume normal short leases after Dominion ends if combat continues.

## Owner priority and safety

Direct Attack Target, Stay, Retreat, HOME and work commands outrank temporary royal directives. Following again permits ordinary coordination, without bypassing the existing target-suppression rules. Existing non-royal targets are not silently replaced. A second King cannot seize another King's active lease, and Kings never command one another.

Ownership changes/loss, death, removal, unavailable owners, disabled orders and task changes clear transient royal sessions and effects. Saved ownership, scale, Momentum and bounded cooldowns persist; a saved ultimate does not replay. Source/session-scoped weak-reference leases expire reliably. Existing authorized foreign-pet duel isolation remains in use; Royal Authority never recruits observers into a pet duel. Existing shared Phantom-family targeting restrictions are preserved.

Ram checks the King and passengers against loaded collision volumes, liquids, hazards and world borders. Block breaking requires a real living owner with build permission, `mobGriefing`, native entity-grief permission, tool suitability, protected-block tags, block-entity exclusion, owner restrictions, entity-destroy permission and the cancellable native block-break event. No ordinary flight destruction, arbitrary stone excavation or unlimited forestry.

## Spawning and taming

- Overworld: Windswept Hills, Old Growth Pine Taiga, Old Growth Spruce Taiga and Meadow.
- Any time; weight 3; solitary group of 1. These are selected initial rarity values because the brief did not specify exact weight/time.
- A bounded surface encounter helper improves discoverability above trees and uneven terrain without changing biomes. It checks at most four players per 10-second pass, searches only loaded safe positions for the full adult volume, allows one nearby wild King, and has a 10-minute per-player successful encounter recovery. Livestock does not prevent this helper from working.
- Territorial, warning unprovoking players before escalation and abandoning a retreating intruder.
- Existing Phantom Staff: instant tame.
- Provisional alternative food: one Enchanted Golden Apple, 100% on the first valid attempt. No new taming-item recipe or UI was invented.

## Controls and testing

Restart the development client to load the new classes and resources. This work does not install a new packaged mod JAR into another launcher.

1. `/summon phantomism:phantom_king ~ ~2 ~`
2. Or `/give @s phantomism:phantom_king_spawn_egg`
3. Tame with the Staff or Enchanted Golden Apple. Use the existing adult riding interaction and an empty hand.
4. Mounted Ctrl: King's Strike at close range or Royal Ram against an aimed enemy farther away. Mounted G: King's Judgment, retaining its documented 20-second utility cooldown. Both use rebindable existing control patterns; there is no invented rapid-fire projectile. Ram temporarily and explicitly hands movement to the server through synchronized state; normal client flight returns on completion or cancellation. The owner receives native authority metadata before a final collision-checked vehicle-position correction.
5. Test Staff Follow, Stay, Attack Target, Retreat and HOME with several owned allies. Ordered/work allies must ignore royal focus.
6. For Dominion, provide at least three legitimate hostile mobs in the open within 40 blocks and let the adult King engage. Observe Coronation, Army and the single Final Decree over 15 seconds.
7. With Momentum 2+, check deliberate Ram through eligible wood with `mobGriefing` both true and false. Ordinary flying must leave terrain intact.
8. Search the four listed biomes during day or night for natural encounters; allow the recovery/cap rules to operate.

## VFX and validation scope

Native royal effects use crowns, gold seals, blue-gold charge ribbons, shield facets, temporary banners and expanding shockwaves. Dominion has three synchronized stages, not fake duplicate allies. Geometry respects existing culling, quality and global budgets (4096 vertices for one cinematic / 1024 ordinary; existing 8192 global ceiling unchanged). The tested King mesh maximum was 1212 vertices. No clocks, Rift portals or new third-party VFX dependency are introduced.

Java 25 compilation, native gameplay, actual registered spawning, PNG decoding, GeckoLib baking, native mesh generation and existing-species regressions passed. They are not a graphical playthrough, a measured FPS result or a live multiplayer latency test. Live crown placement during all poses, camera clearance while riding, animation aesthetics, simultaneous ultimates, real network latency and survival balance still need observation.

The requested Gradle build was attempted with Java 25, but this machine still fails before build tasks with `Unable to establish loopback connection`. No successful Gradle build or new distributable JAR is claimed. Tested development classes/resources are provided through the existing local workflow instead.

See `CHANGELOG_PHANTOM_KING.md`, `PHANTOM_KING_TEST_REPORT.md` and `PHANTOM_KING_CHANGE_MANIFEST.json` for results and exact file lists. Private validation harnesses are included in the source ZIP under `validation/king/`, not deployed as mod gameplay classes.
