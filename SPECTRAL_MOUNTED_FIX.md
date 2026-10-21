# Spectral mounted combat and solid-wall entry

## Changes

- Spectral Lance mounted cooldown: **6 ticks / 0.3 seconds**. The AI cooldown remains **240 ticks / 12 seconds**; damage and 26-block range are unchanged.
- A rider pressed against ordinary solid terrain no longer needs a hole or the extra clearance reserved for safe exits. Entry checks the actual mount/rider bodies and retains a nearby fully cleared recovery anchor.
- Forward momentum of at least **0.25 blocks/tick (5 blocks/second)** enables anticipatory entry. The server checks six ticks of horizontal travel plus 0.5 blocks, capped at **4 blocks ahead**. Slower forward flight retains close-range entry.
- Entry considers the swept volume of both the Phantom and rider, not just an open endpoint beyond a wall. It runs during native mounted movement and server ticks without adding a keybind or client-authorized no-clip.
- Turning away or abandoning the approach before entering terrain cancels the anticipation without consuming a cooldown.
- Once inside terrain, there is still **no traversal timer**. The existing **20-second Passage cooldown begins after safe exit**. Loaded-terrain checks, forbidden blocks, ownership checks, safe dismount/recovery and persistence remain in place.

## Scope

Only `SpectralPhantom.java` and `SpectralPassage.java` change in production source. No shared riding code, other species, textures, models, accessories, renderers or VFX assets are changed.

## Verification

- All 222 production Java sources compile against the cached Minecraft/NeoForge libraries using Java 25; existing deprecation warnings remain.
- **701 Spectral gameplay assertions and 26 spawning assertions pass** on the native server. Coverage includes automatic full-solid-wall traversal, real mounted vehicle packets, fast approach, stationary contact, turn-away cancellation, protected-block rejection, unchanged terrain, safe exits, no traversal timer, cooldown persistence/remounting and unchanged AI recovery.
- Broader native server suites pass for the existing roster, mounted flight, dimension travel and shared VFX/networking.
- The normal offline Gradle build still fails before executing project tasks: `java.io.IOException: Unable to establish loopback connection`. Developer class output is refreshed after validation; no packaged JAR is claimed.

## In-game check still needed

Restart the development client. Ride her directly toward an unbroken mountain face while holding forward: fast flight should start the phase before contact, without needing a hole. Fly entirely out, confirm normal collision returns, and check the existing 20-second Passage recovery. Verify G fires at 0.3-second intervals. A real multiplayer/client-latency session remains a manual test.
