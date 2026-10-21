# Mirage Phantom #38 - The Master of Illusions

Female. Controller / illusion support / secondary damage. Uses the existing shared adult and baby Phantom geometry; no additional horns, ribbons or model parts.

## Attributes and encounters

- 60 HP, 6 attack, 6 armor, 2 toughness, 25% knockback resistance, 48-block awareness.
- Flight attribute 0.5 represents the 10-block/sec design baseline. Existing shared mounted acceleration/cruise behavior is retained; this is not a claim that mounted cruising is capped at 10 blocks/sec.
- Ordinary melee interval: provisional 1.8 seconds. Adult riding and shared membrane feeding/breeding remain available.
- Overworld Flower Forest, Meadow and Dark Forest, day or night. Biome weight 6, groups 1-2, plus the established loaded-chunk encounter system. At most two nearby wild Mirages per 160-block encounter region; successful player encounter retry delay is two minutes.
- Phantom Staff instant tame. Glow Ink Sac: 75% on the first eligible attempt, guaranteed by the second. The attempt counter persists. Spectators and attempts against an already owned Phantom cannot consume a valid attempt.
- Discovery advancement: **The Master of Illusions**.

## Controls

- Existing rebindable G projectile control: Mirage Bolt, 39-block range, 8 base damage, 0.3-second mounted cooldown.
- Separate AI Bolt cooldown: 10 seconds. Switching riding state does not turn the AI clock into a rapid-fire clock.
- New rebindable Mirage Ctrl action in the existing mounted routing: Illusory Strike.
- Existing Staff screen, **Illusions** category: Mirage Copies, Phantom Double, Mirage Field, Hall of Mirrors. Select the individual owned adult Mirage; server checks ownership, distance, command state and cooldowns.
- Follow, Stay, Attack Target, Patrol, HOME and Retreat/Cancel use the existing command architecture. Stay, HOME, incompatible work assignments, removal and cancellation dissolve active illusions.

## All ten abilities

Values not explicitly fixed in the brief are conservative initial tuning, not proven gameplay balance.

| Ability | Implementation and provisional tuning |
| --- | --- |
| False Presence | Real target redirection for explicitly supported vanilla hostile AI, not a particle-only distraction. Scans at most 24 nearby threats and switches at most 12 candidates per pass. Visibility, distance, aggression, decoy type and active fields influence a 15%-85% chance; defensive rescue can guarantee an otherwise eligible switch. A 3-second per-enemy switching lease survives a decoy shattering. Valid original targets are restored when the copy disappears. |
| False Opening | The real Mirage gains 25% bonus damage against an enemy tracking her own valid decoy. Consumed by one successful real attack, then unavailable for 3 seconds. No stacking and no decoy damage. |
| Mirage Copies | Up to three self-copies within the common budget, normally visual; AI selects physical copies when a protectee is injured or facing a close attacker. 12-second cooldown. Normal visual lifetime 5 seconds; physical 6 seconds. Copies have separate positions and bounded flight movement. |
| Phantom Double | One tactically selected allied appearance or self, with fake species-specific attack poses. A defensive double is physical. 16-second cooldown, 8-second lifetime. Shares all other copy limits. |
| Mirage Bolt | Genuine server-authoritative magical projectile with swept native hits, no friendly damage, 8 base damage, 39-block total travel including muzzle offset. AI 10 seconds; mounted 0.3 seconds, independent clocks. Invalid vectors are rejected before VFX creation. |
| Illusory Strike | Creates a short afterimage when budget permits, follows a real collision-checked approach and strikes once for 1.5x attack: 9 base damage. 12-second cooldown, up to 0.9 seconds approach, 12-block acquisition range. False Opening may enhance that real hit. Mounted movement explicitly hands authority to the server and back without moving on the wrong logical side. |
| Decoy Rescue | Places a real 6-HP defensive construct between an endangered ally and a legitimate attacker, redirects eligible hostile attention, and can spend its remaining health absorbing an actual incoming hit. First meaningful allied incoming damage can trigger rescue immediately; periodic threat/projectile scans also deploy it. No forced ally teleport. 18-second cooldown, 5-second construct lifetime, 20-block allied rescue reach. |
| Mirage Field | 12-block field for 8 seconds, 30-second cooldown. Improves the bounded deception probability rather than blinding the player or globally rewriting mob AI. |
| Mirror Guard | Reactive 20-second cooldown. If a physical copy is available within 8 blocks, attempts a clear, loaded, unmounted exchange and consumes the copy to absorb at most its 6 remaining HP. Otherwise provides 25% mitigation capped at 4 HP for that one hit. Mounted Mirage does not forcibly swap the rider. No environmental invulnerability or cooldown reset. |
| Hall of Mirrors | Exactly 90-second cooldown, 12-second duration and 20-block radius. Personal limit increases to six, still under the owner-wide budget. Mixed real constructs and visual actors form a false army; destroyed actors can be replenished at two bounded milestones. The last two seconds strengthen eligible misdirection and converge the mirrors. Finale is one real 10-base-damage hit per valid enemy, at most eight targets, followed by normal-cap cleanup. No terrain damage or boss stun. |

## Unified construct and disguise architecture

`MirageDecoy` is a temporary MISC-category `PathfinderMob`, **not** an `AbstractPhantom`, tameable pet, player or copied species. Its own melee is disabled; it has no copied AI, powers, inventory, owner commands, breeding, loot or XP. Visual mode has 1 HP and physical mode has 6 HP. No-save registration, lifetime checks and caster/session/owner validation prevent persistent ghost copies.

- Personal limit: 3 normally, 6 during Hall of Mirrors. Owner-wide limit: 12, across that server's dimensions. Server-wide safety ceiling: 192. One weak server-scoped registry enforces both modes together.
- A physical copy has a fixed 0.95 x 0.8 hit box regardless of disguise size. It is pickable for combat but not a solid wall for players.
- Visual copies shatter when a hostile projectile strikes, but that projectile continues through. Only physical copies explicitly intercept eligible hostile arrows. Friendly arrows pass through both modes. The interception whitelist includes vanilla arrows/spectral arrows, not tridents, boss fireballs or arbitrary mod projectiles.
- Source death/removal/unload, owner logout/dimension mismatch, incompatible orders, expiry and reload remove copies. Cooldowns remain persisted instead of being reset by reload.
- All 38 currently registered shared-model Phantom presentations resolve to their actual adult/baby texture, animation resource and original per-species accessory flags. Allied authorization requires the same owner or an explicit allied team relationship, not merely being another Phantom.
- Phantom King copies preserve the 2x appearance and bounded crown callback; Golem copies preserve 1.8x appearance without receiving Golem stats. Angel copies preserve the existing halo using a single top-face render to avoid coplanar flicker.
- Gender-specific species presentation is retained through the actual species textures/accessory profile. There is no separate randomized gender system to duplicate.
- Fake projectiles are VFX only. Fake attack poses never call the original species' ability methods. No real King, Golem, Creator or other powerful entity is instantiated as a disguise.
- Supported deception targets are vanilla zombies/husks/drowned/zombie villagers, skeleton variants, spiders/cave spiders, pillagers/vindicators/evokers, witches, silverfish and endermites. Bosses, arbitrary modded AI and scripted targeting are not claimed to be fooled.
- Existing ordered foreign-pet duel isolation is checked before protection or deception. Mirage does not enlist the family against another player's companion merely because a duel was ordered.

## Appearance, animation and VFX

Saved templates are recolored iridescent lavender and silver-white with cyan/turquoise reflective wing markings. Adult and baby skins keep the exact template transparency/UV mask. Calm eyes are pink; combat eyes are cyan. The Mirage renderer alone adds a subtle animated translucent shimmer; no full-screen shader/refraction dependency is introduced.

Twelve dedicated action clips supplement shared flight, idle and basic movement: copies, double, bolt, strike, rescue, field, guard, hall, convergence, finale, swap and shatter. Disguises use real compatible attack clips with an explicit shared fallback. Crystalline amethyst chimes provide restrained casting audio.

Four dedicated mirror textures drive the existing NeoForge-native VFX framework: concentric sigils, translucent angular panels, floating fragments, slender trails, fake bolts and final shattering rings. Hall rises into the arena, sustains the reflective panels and collapses at the finale. No physical blocks are spawned, and Chrono clocks/Rift portals are not reused. Maximum tested Mirage effect: 852 vertices within the unchanged 1,024 per-effect and 8,192 shared-frame budgets.

## Validation and files

See `MIRAGE_PHANTOM_TEST_REPORT.md` for automated versus manual evidence, `MIRAGE_PHANTOM_CHANGE_MANIFEST.json` for every added/modified file and SHA-256, and `CHANGELOG_MIRAGE_PHANTOM.md` for the concise change summary.
