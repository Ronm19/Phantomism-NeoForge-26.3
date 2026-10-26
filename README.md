
Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/

Phantom Codex:
============
Players receive one starter Codex per world; a full inventory delays delivery until a slot is free.
Craft replacements with one Book and two Phantom Membranes in any arrangement. Right-click to open the guide.
The directory follows the official 58-species roster. Meet a species to unlock its field notes; entries without notes remain neutral placeholders.
Approach within 16 blocks or interact directly to earn the existing discovery advancement and unlock details.
Progress belongs to the player's server advancements, not the book. The crafting guide also shows the unchanged Staff recipe.
Taming guidance reads each entity's current item definition; some species are Staff-only, and attraction items are listed separately.
Discovered pages group verified information into Overview, Combat Profile, Abilities, Taming, Riding and optional Field Notes.
Undiscovered snapshots omit these private profiles entirely. Search uses only public identification and habitat clues.
Cached client-side head portraits use the registered entities, shared models, real textures and existing accessories; no new head items or geometry are added.
Getting Started explains actual Staff, HOME and flight controls. Progress counts discoveries, not development status.
The Codex recolors the supplied book icon in indigo, violet and cyan, preserving its shape and transparency without modifying shared Phantom models or skins.

Spore Phantom #045:
==================
Common, peaceful fungal companion in Swamp, Mangrove Swamp and Mushroom Fields, day and night (weight 12; groups 1-2).
Uses the unchanged adult/baby Phantom models with only the existing mushroom accessory enabled and hand-recolored moss/ivory/red textures.
Defaults: 72 HP, 6 attack, 10 armor, 2 toughness, 35% knockback resistance, 48-block awareness; controlled flight attribute 0.4.
Red or Brown Mushrooms share 50% taming success and a guaranteed third eligible attempt. Phantom Staff remains an instant alternative.
Spore Breath: 8 damage, Poison I for 3 seconds, 42-block range; 10-second AI cooldown or 0.3 seconds mounted (G).
Breath clouds: radius 2.5, 2 seconds, 1 damage per second; maximum three gameplay clouds per caster.
Mushroom Bloom: radius 8, 8 seconds, 1 HP/second allied healing and enemy Slowness I; cooldown 20 seconds. Automatically helps injured owner/allied pets.
Decay Dive: 14 damage and 3 seconds of Weakness I; maximum 18-block approach; cooldown 12 seconds. Ctrl arms a mounted impact without server-forcing rider movement.
Fungal Storm: radius 18, 12 seconds, 3 damage/second with Weakness I and Slowness I; allied healing 1 HP/second; cooldown 90 seconds.
Wild Spore only defends itself. Tamed Spore defends its owner/pets and follows explicit attack orders; no indiscriminate poisoning of peaceful animals.
Stay, HOME, Retreat, noncombat tasks, owner logout and removal cancel active fungal effects. Cooldowns persist; active areas never replay after reload.
Mushroom growth is visual only: no permanent terrain edits. Fungal VFX use the existing network and client budgets.

Solar Phantom #046 and Staff Abilities:
======================================
Solar is a peaceful, rare, solitary daylight companion in open-sky meadows, peaks, savannas and badlands.
Uses the unchanged shared adult/baby geometry, recolored white-gold/orange templates and existing horns/halo.
Defaults: 300 HP, 20 melee, 18 armor; direct clear Overworld sunlight improves damage, healing, recovery and flight.
Glowstone Dust has 35% taming success (guaranteed third attempt); Golden Apple 75% (second); Staff remains instant.
Solar Annihilation shares Quick/Charged recovery (3/7 seconds AI and Staff); 42-block range; mounted Quick Beam uses 0.3 seconds.
Sanctuary shares its standard/emergency recovery; Blitz shares quick/comet recovery; Supernova shares Judgment/Salvation recovery.
Solar terrain melting and surface scorching are optional and disabled by default. Temporary illumination is allied night vision, not placed light blocks.
The existing Solar-Lunar interface and mutual-consent pact storage now connect to Lunar #049; same-owner companions cooperate automatically, and different-owner cooperation requires mutual authorization.

The Staff keeps recipient arrows and All Nearby for ordinary commands. Sections are Movement, Combat, Activities, Home & Recall, Abilities, and relevant Rift/Golem tools.
Abilities require one selected owned Phantom. Cooldown status is read from the server and from each native ability's real timer; there is no Staff-only ability cooldown.
Choosing a targeted ability uses the normal Attack Target order, then requests that ability. Native range, stance, charges and safety requirements still apply.
Manual modes are currently connected for 33 species. Other passive, reactive and unsafe-to-command abilities are explicitly automatic-only; their AI remains unchanged.
For connected projectile abilities, AI, Staff and mounted use now share the same timer. A successful mounted shot uses its established short interval; it cannot bypass an existing AI/Staff recovery.
Rift portals/anchors/transportation, Golem excavation/storage/delivery, home, guard and ordinary group commands retain their original network identifiers.


### Illager Phantom #047 — The Dread Commander

Shared-model charcoal, silver and burgundy commander with emerald eyes; 280 HP, 16 armor and 18 melee damage. Four active abilities share their native cooldowns across AI and Staff. Mounted Barrage uses 42 blocks and six ticks; Emerald taming is 50%, guaranteed by the third attempt.

Independent uncommon encounters use loaded, generated outpost metadata or a single saved chance per active raid, with one nearby wild commander. This entity is not a Raider and never joins waves or alters raid membership. Its reversible charm protects the owner and allied companions without stopping unrelated vanilla hostility; an attacking raider individually loses protection. Temporary Vexes, fangs and bolts have bounded populations, ownership checks and cleanup. Retreat interrupts Conquest without refunding cooldowns. All Nearby Staff selection remains available.


### Infernal Phantom #048 — The Blazing Outcast

Original V2 identity retained: Infernal Heat, Infernal Adaptation, Infernal Pressure, Infernal Bolt, Hellrush, Infernal Field, Overheat, Ash Burst, Cinder Guard and Hell Ascendant. New numerical defaults implement the approved proposal rather than asserting undocumented canonical values. Heat is server-owned, saved and synchronized; Overheat consumes it without self-feeding. Hold the existing G control for one aimed bolt every two ticks (ten per second, 32 blocks); AI and Staff use controlled three-shot bursts. Native cooldowns are shared. Blaze Powder taming uses 50%, guaranteed on attempt three; the Staff remains instant.

Rare solitary Nether Wastes/Basalt Deltas encounters allow safe air above solid terrain or lava, never new Overworld habitats. Shared horns/spikes and original UV/alpha layouts are preserved; textures are directly recolored without image generation. Fire/lava immunity is specific, allies are protected, fields/eruptions do not alter terrain, and Hell Ascendant budgets every phase. Existing Staff tips and All Nearby controls remain intact.

### Portal follow and emergency horn rally

Loaded owned companions in Follow or owner-Guard immediately abandon combat when their owner changes dimensions, using the successful dimension-transfer event rather than waiting for the old one-second polling interval. This also covers custom portals using normal dimension transfers. Stay, fixed guard, home/work orders, leashes and foreign passengers remain respected. Safe loaded arrival checks, native travel cancellation and mounted portal/End-credit handling remain in place; blocked arrivals pause combat and retry with bounded searches instead of forcing terrain or chunk loads.

Blowing any goat-horn tune, including Seek, explicitly recalls all loaded owned, unmounted and unleashed Phantoms. Within 40 blocks they immediately stop fighting and fly back quickly, tracking the moving owner; farther away or in another dimension they safely teleport beside the owner. The horn switches recalled companions to Follow and clears work assignments without deleting home positions or cargo. Returning companions ignore old combat targets until close to the owner, then resume normal protection. New Staff orders override the rally. Vanilla horn sound and cooldown remain unchanged; unloaded companions are not force-loaded.


### Lunar Phantom #049 — The Moonlit Sovereign

Lunar Aura grants her owner and authorized allied players continuous, non-flickering night vision within a spherical 30-block radius, including while stationed, mounted or in combat. Walls and time of day do not interrupt it. Leaving the radius, losing the source or changing dimensions removes only this aura; overlapping Lunars and unrelated potion/command night vision are preserved.

Total Eclipse now presents a camera-facing spectral eclipse, gold/blue partner streams, expanding shockwaves, actual-recipient healing beams, a decorative celestial dome and restrained positional sound cues. Existing instantaneous damage, healing, Resistance, partnership, AI and recovery remain unchanged; presentation phases overlap instead of delaying gameplay. One shared event and at most eight healing events use the existing VFX transport, duplicate suppression, 64-effect/8192-vertex limits, distance reduction and cinematic density settings. Partner interruption, disappearance and world changes cancel the presentation. GPU appearance and multiplayer audio still need an in-game check.

Five original passives, Moon Lance, Moon Fang, Lunar Rush, Crescent Slash, Moonbreak, Moon Pulse, Moonlit Veil and Full Moon Ascendancy are implemented using shared models and native authoritative cooldowns. Charge and Pressure are separate saved, synchronized 0–3 resources. Natural moonlight can build Charge; only legitimate hits build combat momentum. Daytime combat remains available. Moon Lance uses 42 blocks, three-second AI/Staff recovery and the approved six-tick mounted interval. Amethyst Shard taming is 50%, guaranteed on the third eligible attempt; Staff taming stays instant.

Rare solitary open-sky nighttime encounters use Meadows, Taiga and Windswept Hills, with no phase requirement. Original adult/baby UVs, alpha and geometry remain unchanged; midnight-blue, silver and lavender textures and existing horns are directly recolored without image generation. The four-phase ultimate lasts twelve seconds, recovers for 110 seconds and shares 65/25 per-target direct-damage budgets across all phases. Retreat, horn rally, removal and dimension travel interrupt temporary combat effects without refunding recovery.

Solar cooperation reuses CelestialBond and CelestialPacts, including mutual authorization, persisted recovery on both partners and the established offensive/defensive/balanced Total Eclipse outcomes. Neither companion requires the other. Codex discovery, portraits, Staff tips, Charge/Pressure/Veil status, existing G/Ctrl controls, immediate portal following and horn recall remain integrated.


### Autumn Phantom #050 — The Fallen Seasons

Permanent year-round female forest guardian. Seven original abilities: Falling Leaves, Harvest Sense, Autumn Bolt, Leaf Rush, Seasonal Guard, Harvest's Gift and Autumn Gale. Proposed stats: 240 HP, 14 armor, 15 melee, 55% knockback resistance. Apples tame at 50%, guaranteed third eligible attempt; Staff remains instant. Autumn Bolt uses the approved 42-block range and six-tick mounted interval; AI/Staff recovery stays five seconds.

Harvesting is disabled until an owner selects a crop/vegetation area or explicitly approves one log in the universal Staff. Only mature supported crops and a conservative plant allowlist are harvested; wood authorization is single-use and checks the exact selected block state. Existing companion cargo delivers drops, with a bounded saved overflow buffer. Replanting consumes actual seeds from the loot. Combat, retreat, other tasks, full cargo, unavailable owners and unsafe/unloaded routes pause work. Owner permissions, mob griefing and break/placement hooks remain authoritative. No tree-felling, arbitrary decorative-block harvesting or invented storage integration.

The installed Minecraft registry provides minecraft:dappled_forest, verified against the native registry and Biomes.DAPPLED_FOREST. Autumn uses this primary habitat directly. The runtime also recognizes registered Dappled Forest paths or the autumn_primary_spawns biome tag; only when unavailable, an enabled-by-default configuration fallback uses Forest, Flower Forest, Birch Forest and Old Growth Birch Forest. Datapacks can supply primary habitats. Spawning remains solitary and population-limited, day/night and year-round.

Original shared adult/baby UVs, alpha and geometry are preserved. Bark-brown/orange/amber/cream skins and existing horns/ornaments use direct palette edits, not image generation. Native leaf-shaped VFX, a coherent autumn tornado and restrained leaf/chime sounds replace fire imagery. GPU appearance and multiplayer play remain manual review items.
