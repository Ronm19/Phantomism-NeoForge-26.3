# Golem Phantom #37 - The Titanium Gigachad

Male, Apex. Minecraft 26.3 / NeoForge 26.3.0.64-beta / Java 25.

## Identity and attributes

400 HP, 16 attack, 30 armor, 12 armor toughness, 95% knockback resistance, 48-block awareness, configured flight attribute 0.3 (the design's 6-block/second value) and a 40-tick ordinary melee interval. Native SCALE is 1.8, including the 2.52 by 1.62 adult collision box and scaled riding seat. These are initial gameplay tuning values, not a claim of balanced playtesting. Existing standardized mounted cruise remains unchanged, rather than imposing a new six-block/second mounted cap.

The existing shared adult/baby model is retained. Titanium panels, seams and rivets are painted into the saved UV templates. All optional accessories are hidden for Golem only. There are no new armor layers, horns, replacement models or global accessory changes. Calm eyes are muted red; server-synchronized combat/protection eyes are emissive cyan with a five-second threat-clear grace period.

## All eleven abilities

| Ability | Implementation and provisional tuning |
| --- | --- |
| Titanium Body | Native armor/toughness/resistance; an additional 15% reduction to eligible ordinary melee impacts; 2 HP natural regeneration every 100 server ticks. Magic, environmental and special attacks are not universally negated. |
| Arrow Immunity | Cancels ordinary vanilla arrows. Tipped, potion-bearing, spectral arrows, tridents and other magical/projectile types are not classified as ordinary arrows. |
| Ore Breaker | Real ore recognition, native loot generation, a virtual unenchanted diamond pickaxe, loot-modifier/drop events, experience and protected block-breaking checks. Never starts mining without an owner job. |
| Village Defender | Recognizes owner/family/self/villager/iron-golem threats, including actual incoming damage and imminent mob targeting. Keeps explicit commands authoritative and excludes observers of authorized pet duels. |
| Golem Excavation | Staff precision selection and bounded two-corner 3D jobs, safe loaded navigation, persistent progress, direct private cargo collection, automatic delivery and resumable defense interruptions. |
| Obsidian Breaker | Separate explicit permission toggle. Deliberate 100-tick mining action, legitimate obsidian drops and portal/fluid/protection safeguards. |
| Golem Ram | 280-tick cooldown; 10-tick windup and physical collision-checked charge; one 32-base impact with resistance-aware knockback. Optional obstacle mode permits at most eight leaf blocks per charge, never buildings or logs. |
| Golem Slam | 200-tick cooldown, 6-block area, 22-base damage, up to 12 eligible targets and bounded launch. One conditional 10-base aerial follow-up, a limited pursuit window, owner proximity checks and conservative boss/heavy-target immunity. |
| Seismic Punch | One adaptive ability: within five blocks, a 24-base hook plus a small 6-base shockwave; farther away, a genuine 20-base titanium fist travelling 1.5 blocks/tick with terrain collision and a 42-block maximum. Hook and fist share the cooldown within each control mode. Mounted cooldown is six ticks (0.3 seconds), independently of the 240-tick AI cooldown. |
| Iron Guard | 600-tick cooldown, 160-tick duration, 12-block radius. Strongest nearby Golem guard gives 45% damage reduction to eligible protected allies; multiple Golems do not multiply this reduction. Intercepts up to four eligible approaching hostile arrows per ten-tick scan. No solid shield collision or blanket projectile deletion. |
| Titan's Impact | Shared 2200-tick cooldown and 200-tick effect window. Grounded Earthbreaker winds up; airborne Skyfall descends collision-safely or releases in midair when landing is blocked/unsafe. One 40-base impact within 16 blocks, at most 24 eligible enemies, bounded knockback and brief disruption. Terrain remains intact. |

## Finding and taming him

This is a special Overworld village encounter, not an ordinary biome spawn-weight entry. A loaded inhabited village receives one persistent deterministic approximately-40% decision. Generated villages use the actual village start chunk; player-made villages use a conservative occupied-HOME cluster. Positive and negative decisions persist under the `phantomism:golem_villages` SavedData identifier. Taming, killing or removing the original guardian does not reroll the village. Nearby existing Golems also prevent duplicates. He guards his village position and does not despawn normally.

The encounter honors natural mob spawning, only checks loaded chunks and never adds chunk tickets. Unloaded structure-start references defer the decision instead of loading terrain.

The existing Phantom Staff instantly tames him. An iron block is the optional first-attempt food tame; this adds no new taming item. Taming returns him to Follow. Shared adult riding, breeding, baby resources and existing companion behavior remain available. No poppy-giving behavior is added.

## Controls and mining workflow

1. Open the existing Staff screen and select **one Golem Phantom**, not All Nearby.
2. **Golem Mining > Precision**: right-click an approved block with the Staff.
3. For a 3D job, select **Area Start**, right-click the first corner, then **Area End** and right-click the opposite corner. Choosing the second corner authorizes and starts the job.
4. **Obsidian +/-** explicitly changes obsidian permission. **Cancel Job** stops work immediately without throwing away cargo.
5. In **Golem Utility**, **Set Chest** selects a compatible inventory by Staff right-click. **To Owner** clears the chest destination and delivers to the owner. **Deliver** requests delivery without cancelling the saved job.
6. **Work Status** reports cargo and permissions. The Staff footer synchronizes the current work state and occupied slots.
7. Existing rebindable **Ctrl** selects Ram at distance or Slam nearby; existing **G** invokes adaptive Seismic Punch. The Staff utility category exposes Iron Guard and Titan's Impact. Mining selection does not reuse mounted attack keys or shift-right-click HOME.

Follow/Stay/Retreat/HOME and new ordinary work commands cancel the mining job rather than leaving hidden destructive work running. Defense pauses a still-authorized job and resumes it when the threat clears. Mounted entities do not mine.

## Deliberately conservative mining rules

- Area jobs mine **ores and explicitly enabled obsidian**, not arbitrary bulk terrain. Precision additionally permits an explicit natural-stone allowlist. This keeps default excavation away from recognizable construction. An enclosed or inaccessible vein can pause until the owner exposes a safe route.
- The default volume limit is 512 blocks, configurable up to 4096, with at most 16 blocks on each axis. The normal work interval defaults to 12 ticks, configurable from 4 to 200. Obsidian always takes 100 ticks.
- Configuration keys: `golemPhantom.maxExcavationVolume` and `golemPhantom.miningTicks`.
- No containers/block entities, beds, doors, unbreakable blocks or general building materials are approved. Village areas, nearby fluids, portals, dangerous/unloaded positions and native permission vetoes stop work.
- `mobGriefing`, entity-grief/destruction hooks, player build restrictions, world-border checks, native break events and inventory-access events are respected. Third-party protection compatibility still depends on those mods honoring the native hooks; legacy player placement cannot be inferred universally from natural-looking blocks.
- Obstacle-breaking Ram is separately authorized and restricted to leaves outside village protection. It cannot plough through wood houses or arbitrary stone terrain.

## Cargo and delivery

The hidden 27-slot container stores exact item components and slot contents in entity save data. Only loot from this Golem's successful mining transaction is collected; there is no general item vacuum. Native transactions roll back partial insertion when cargo cannot fit a block's full drops. The block is not removed in that case.

Owner delivery uses only the owner's normal 36 inventory slots. Chest delivery uses NeoForge's transactional `Capabilities.Item.BLOCK` resource handler. Compatible sided inventories may reject individual items; rejected items remain in cargo. A designated inventory is marked with the authorizing owner's UUID and checked for access and ownership. Missing, full, inaccessible or unloaded destinations pause safely. The owner may be distant while the loaded route and designated chest remain accessible; an offline or different-dimension owner pauses work rather than bypassing permission checks.

Jobs and cargo survive normal save/load. Dimension-mismatched jobs pause. Death drops retained cargo once and clears the internal inventory. Cancel and command changes do not delete cargo.

## Native animation, VFX and multiplayer

Twelve Golem-only action clips extend the existing shared movement animations: hook, fist, Ram, Slam, follow-up, guard, excavation, obsidian, collection, delivery, Earthbreaker and Skyfall.

The native VFX system now contains dedicated titanium sigils, fist geometry, geometric shields, charge trails, impact flashes, expanding cyan/silver shockwaves, debris illusions and vertical impact streaks. Existing metallic/impact sounds provide layered audio. There are no fake terrain blocks, persistent cinematic entities, royal crowns, Rift portals or Chrono clocks.

Existing global effect/vertex budgets remain unchanged. Client tests exercise low-budget fallbacks and finite geometry. Damage, mining, cooldowns and movement are server-authoritative. Forced mounted Ram/Skyfall temporarily claim vehicle movement using synchronized state, then return control with native metadata/vehicle packets, preserving the King's earlier movement-side fix.

## Resources and delivery

- Adult/baby skins: `assets/phantomism/textures/entity/golem_phantom.png` and `golem_phantom_baby.png`.
- Eye overlays: matching `_eyes.png` and `_combat_eyes.png` files for both ages.
- Native VFX textures: `assets/phantomism/textures/vfx/titanium_{sigil,shield,fist,trail}.png`.
- `GOLEM_PHANTOM_CHANGE_MANIFEST.json` lists every created/modified source/resource file and preservation counts.
- `GOLEM_PHANTOM_TEST_REPORT.md` distinguishes successful automated validation from remaining on-screen/multiplayer checks and explains the local Gradle blocker.

The supplied ZIP is a source-project snapshot, not a newly Gradle-built mod JAR. Restart the development client to load the updated development classes and resources.
