# Angel Phantom #29 — implementation report

## Approved design

Female Elite support/healer/guardian. The written V2 brief takes precedence over the image's conflicting Apex statistics, Radiant Feather item and alternate ability names. Native breeding has no species-gender mechanic; its existing family rules are unchanged.

Base attributes: 76 HP, attack 5, armor 10, toughness 4, knockback resistance 40%, awareness 56, scale 1.0, melee recovery 40 ticks. Flying-speed attribute 0.425 follows the project's 8.5-block/sec profile convention; owner-ridden movement retains the existing shared fast-flight behavior.

- Tame with one Golden Apple; first valid attempt succeeds. Existing creative consumption rules apply.
- Empty-hand mount; normal movement, ascent/descent and Shift dismount remain unchanged.
- Rebindable Ctrl: Holy Dive. Shared rebindable G: Angel Smite.
- User-approved Smite override: **42 blocks, 0.3 seconds mounted**. AI recovery remains 12 seconds; damage remains 8 HP.
- Overworld Meadow and Cherry Grove, weight 3, singleton. Registered encounter helper works in explored terrain without relying on an empty passive mob cap. Daytime attempts are preferred; night remains eligible with a one-in-four helper attempt chance and a longer successful-encounter delay. Local wild population is capped at one within 160 blocks of an observer.
- Follow, Stay, Attack Target, Retreat, HOME, recall, ownership, breeding, discovery, riding and dimension travel use the existing architecture.

## Implemented abilities and provisional tuning

| Ability | Server implementation |
|---|---|
| Harmful Immunity | Rejects supported harmful effects, not raw damage. Uses harmful-effect classification plus `phantomism:angel_cleanseable`; beneficial effects and unlisted boss/mod mechanics remain untouched. |
| Blessed Presence | Combines Brain memories with a 56-block allied scan once per second; retains at most 64 candidates. Prioritizes a critical owner/rider, critical companions, dangerous effects, then lesser injuries. Does not extend actual healing range to 56 blocks. |
| Mounted Regeneration | Bonded adult rider receives Regeneration I for 80 ticks, refreshed only when at most 40 ticks remain. Every 10 ticks, cleanse the rider and up to 64 eligible nearby allies within an exact 20-block sphere, with line-of-sight checks. Supported new rider debuffs are rejected immediately. Dismount stops refresh and immediate rider immunity; existing short Regeneration expires naturally within four seconds. |
| Angel Touch | Direct 12 HP heal, range 16, cooldown 20 seconds. Requires at least 2 missing HP; autonomous use normally requires 4. No healing above maximum, dead-target healing or cooldown consumption on a rejected/cancelled heal. |
| Angel's Blessing | Range 16, cooldown 15 seconds, duration 6 seconds. Initial mitigation 15%, finite 6 HP prevention reservoir, supported-effect cleansing and short resistance to new supported debuffs. Requires a genuine threat. |
| Heal Trail | Moving support state for 8 seconds, cooldown 20 seconds. Up to 12 segments, each lasting 3 seconds; emission every 5 ticks requires movement and a new spatial position. Allies within 2 blocks of a live segment can receive 1 HP per 40 ticks, within a 24-block source leash and line of sight. No trail entities or hostile healing. |
| Angel Smite | Real server-colliding projectile, 8 HP before armor, 42-block total travel including muzzle offset. AI 12 seconds; mounted 6 ticks. Shared saved history prevents remount/reload refunds. No armor or boss-immunity bypass. |
| Holy Dive | Cooldown 12 seconds; one real contact hit at 1.5 times attack, initially 7.5 HP. Bounded solid/fluid/loaded-space movement checks. Mounted use never commandeers the pilot's movement. |
| Angel Shield | Meaningful reactive danger trigger, range 16, cooldown 24 seconds, duration 4 seconds. Initial 30% mitigation with an 8 HP prevention reservoir. No Absorption or invulnerability. |
| Divine Intercept | Range 8, cooldown 16 seconds, short 2-second intervention: 20% mitigation with a 6 HP reservoir. A nearest-ready-Angel claim expires after 8 ticks. Unmounted safe movement can approach the ally; mounted flight input is untouched. It can react to the first genuine incoming hit before attack history exists. |
| Eye of the Angel | Official **100-second cooldown, 12-second duration, 24-block radius**. One immediate, direct 18 HP group heal per eligible recipient in the activation snapshot (at most 32), plus supported cleansing. Initial defensive support is 12% with a 12 HP prevention reservoir, bound to the original sanctuary area. Late arrivals never receive a second full heal. |

Touch, Trail and Eye healing amounts, Blessing/Shield/Eye mitigation and Shield capacity have conservative common-config entries under `angelPhantom`. Values above are initial defaults, not playtested final balance. Cooldowns are saved; interrupted active effects are not replayed on load. Chrono may assist eligible normal cooldowns, never Eye's ultimate cooldown.

Eye requires three meaningfully injured allies, two severely injured allies, two allies with dangerous supported effects, or a critically wounded owner/rider with recent genuine attack history. Tiny injuries and short minor debuffs do not justify the ultimate. It consumes no cooldown unless some healing or cleansing actually commits. Other support abilities retain their independent cooldowns; the ultimate itself does not heal every tick.

All healing uses `LivingEntity.heal`, never instant-health splash logic. Zombie, Skeleton, Husk and other friendly undead therefore receive normal healing. Native heal events and maximum-health rules remain respected.

## Safety, reservations and targeting

- Angel never intentionally damages her owner, passengers or protected allies. Projectile validity is rechecked at collision. Hostile damage retains native armor, invulnerability and immunity rules.
- Wild Angel is peaceful; autonomous attacks require genuine self/family danger. Explicit staff attacks remain supported. Urgent healing takes priority over generic pursuit.
- Supported cleanse effects initially include Poison, Wither, Slowness, Weakness, Blindness, Nausea, Hunger, Darkness and Mining Fatigue. The tag is data-pack extensible, but only harmful-classified effects qualify. Levitation, beneficial buffs and unknown boss mechanics are not blanket-cancelled.
- One strongest Angel ward per recipient prevents additive Angel stacking. Each reservoir is finite; source death/unload, invalid ownership, range and expiry revoke it. Environmental and explicitly bypassing damage are not intercepted.
- Emergency-heal claims are weak-reference, bounded and released after completion. A short native-heal observation window coordinates with other healers; still-critical allies may override that recent-healing gate. Intercept claims follow Sentinel's scoped reservation pattern without changing its species-typed API or shared geometry.
- Authorized foreign-pet duels remain scoped using `PhantomDuels`; observers do not convert them into family retaliation or unnecessary defensive intervention.

## Appearance, animation and VFX

Shared adult/baby Phantom models are reused unchanged. **Only Angel enables the existing `halo` group**; horns, spikes, mushrooms, tendrils and fangs are hidden. No new model bones or accessory variants are generated.

Species textures are locally recolored from saved templates using the real shared-model UVs: pearl/ivory, champagne gold and celestial-blue details. Both existing halo UV faces receive a transparent-center gold ring and emissive overlay. No image generation is used.

Halo flicker correction: Angel's renderer alone suppresses the zero-thickness halo's overlapping top/bottom faces in the ordinary body and eye-overlay passes. `AngelHaloLayer` submits the existing upper face once using a two-sided, full-bright emissive material. The halo retains its original texture, head animation and adult/baby placement without editing the shared model, cached geometry, other species' renderers or accessory defaults. Invisible Angels inherit the entity's visibility material rather than gaining an opaque halo.

Texture paths:

- `src/main/resources/assets/phantomism/textures/entity/angel_phantom.png`
- `src/main/resources/assets/phantomism/textures/entity/angel_phantom_baby.png`
- Matching `_eyes.png` overlays; `textures/item/angel_phantom_spawn_egg.png`.
- VFX assets: `textures/vfx/angel_eye.png`, `angel_feather.png`, `angel_sigil.png`, `angel_trail.png`.

Ten real GeckoLib ability clips supplement the shared idle/flying/follow/tame/breeding animation set: Touch, Blessing, Trail, Smite, Dive, Shield, Intercept, Eye activation, conclusion and ordinary strike. Native trigger networking is retained; animations do not lock mounted controls.

Eye VFX stages: wing-spread/awakening sigil, opening geometric celestial eye with rotating iris, expanding sanctuary wave, white-gold spiral ribbons, hovering rings and falling feathers, then eye closure and a feathered conclusion. This is a magical symbol, not a biological eyeball. Healing links and feather-edged shields use separate readable effects; Smite has a pale-blue/pearl core with a narrow gold spear trail. No clocks, portals, world lighting or world-time changes.

All visual events use existing nearby-client networking and source/session cancellation. Per-ultimate recipient links and shield cues are capped at eight each, preserving the main cinematic event under the normal packet budget. The existing 64-effect/8192-vertex total frame limits remain unchanged. Native quality/budget testing reaches 1476 vertices for Angel's largest single effect, below its 4096 cinematic allowance. Offline preview images use actual submitted vertices, UVs and colors, not an in-game GPU screenshot.

## Files

New Java files: `entity/AngelPhantom.java`, `entity/AngelSmite.java`, `entity/ai/AngelSupport.java`, `entity/spawn/AngelEncounterSpawner.java`, `vfx/AngelVfx.java`, `client/vfx/CelestialVfx.java`, `client/renderer/AngelPhantomRenderer.java`, `client/renderer/AngelHaloLayer.java`, `datagen/ModAngelAnimationProvider.java` (under `src/main/java/net/ronm19/phantomism`). New assets: nine species-only PNGs, the cleanseable-effect tag and eight generated animation/item/discovery/loot/spawn/biome resources.

Modified integration files: `Config`, `Phantomism`, `PhantomismClient`, `PhantomDiscovery`, `PhantomismContent`, `ModSpawnPlacements`, `ModTags`, `PhantomismNetwork`, `PhantomVfxTheme`; five existing data providers plus `ModDataGenerators`; English translations; generated Phantom/entity and spawn-egg item tags. Native VFX registry/dispatch/budget selection and bounds are extended only for Angel's forms and healing links. No existing entity, Brain AI, shared riding, model or renderer source is changed.

## Combat and teleport stability correction

The reported `Invalid VFX vector` server crash originated in Heal Trail after a teleport: its previous node could be more than the packet's 128-block vector limit away. Trail jumps longer than 16 blocks now start a fresh local segment instead of drawing a cross-world ribbon. Angel's outbound visual helper also rejects non-finite/out-of-bounds cosmetic coordinates without throwing through the entity tick; the shared packet constructor and network validation remain unchanged and strict.

Angel no longer consumes every shared combat tick while doing nothing between special abilities. She can use ordinary 5-HP melee with its existing 40-tick interval, pursue through the shared Brain/flight system while specials recharge, and start Holy Dive within its full existing 12-block reach. Healing still runs independently. Critical allies only override movement when Touch is ready, the patient is outside healing range, a loaded clear route exists and a collision-safe step is possible; an injured Angel or nearby wounded ally no longer indefinitely disables fighting. Peaceful wild behavior, owner/ally protection, staff targeting and scoped duels remain unchanged. Smite retains 42-block range, 12-second AI recovery and 0.3-second mounted recovery.

## Build and verification

- All 231 production Java sources compile with Java 25 against the actual cached Minecraft 26.3, NeoForge 26.3.0.64-beta and GeckoLib libraries. Existing deprecation/unchecked warnings remain.
- Final native Angel suites pass **322 gameplay assertions and 25 spawning assertions**, including 56-block support sensing despite populated Brain memory, first-hit emergency defense, mounted cleansing, safe undead Touch/ultimate healing, one-time ultimate delivery, scoped duels, actual projectile collision and cooldown persistence. New regressions reproduce the original trail-vector crash and blocked melee before the fix, then verify teleport discontinuities, strict packet validation, real melee while both specials recharge, support/combat coexistence and a live Brain/flight pursuit that lands a hit before either special is ready. Logs: `work/phantomism-private/angel-combat-fix` in the chat workspace.
- Native client validation: 1,621,206 Angel geometry, UV, animation, halo and budget assertions pass. All Chrono, Rift, Sentinel, Demon and Spectral client suites also pass. Native data generation produces 259 resources.
- Halo correction: 3388 additional native checks reproduce the original coplanar faces, then verify a single deferred halo quad across adult/baby, mounted/unmounted and 20 view rotations, inherited head animation/UVs, visibility handling and no shared-model state leakage. The existing six species' client/VFX suites pass again. Actual GPU/shader behavior still needs an in-game check after restart. Logs: `work/phantomism-private/angel-halo-fix` in the chat workspace.
- Existing-roster server regression passes include 196 mounted-flight checks, 384 existing-projectile range checks, 1008 native dimension-travel checks (including Angel heal/ultimate cooldown preservation), and 2344 shared VFX/network checks across 29 species.
- The normal offline Gradle build still fails before project tasks: `java.io.IOException: Unable to establish loopback connection`. Developer class/resource output is refreshed after validation; no packaged JAR is claimed.
- Hash audit preserves all 195 existing PNGs, eight model files, 37 existing renderer sources, 89 existing entity/AI sources and 26 Chrono/Rift/Sentinel/Demon specialist sources. No global halo/horn/accessory defaults are changed; Spectral's momentum passage and 0.3-second mounted cooldown remain intact.

Private native validation and offline previews are saved in the chat workspace at `work/phantomism-private/angel-implementation`. Final native gameplay/spawn counts are recorded in `server-final.log`.

## Live tests still needed

Restart the development client. Test actual natural Meadow/Cherry Grove encounters, Golden Apple taming, staff orders, adult riding, Ctrl/G, ordinary mounted damage and dismount Regen expiry. Injure friendly Zombie/Skeleton/Husk companions and confirm Touch/Trail/Eye heal rather than harm them. Trigger Eye with a genuinely injured group; take more damage during its 12 seconds to confirm there is no repeated full ultimate heal. Check cleanse whitelist behavior, finite shields and multiple-Angel reservations in a real fight.

Manual multiplayer/client-latency, shader/transparency/camera readability and actual GPU performance remain unverified. Initial healing and mitigation values require gameplay balance feedback.
