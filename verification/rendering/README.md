# Native vertex and real-client regression

`RenderRegression.java` is a developer-only test mod fixture. It is outside the
production source set and must never be included in the Phantomism release JAR.
The ordinary Gradle test task has no test sources; this test requires a real
Minecraft client, its installed NeoForge runtime and native graphics libraries.

The fixture checks:

- The old four-attribute submission against vanilla BLOCK, native ENTITY and
  Iris TERRAIN formats, including both next-vertex and final-vertex validation.
- Overlay-only versus Normal completion of Iris's extended format, isolating
  the missing Normal as the failure.
- Production VfxMesh callbacks with 1, 2, 5, 10, 20 and 64 beacon branches, opaque
  cores and translucent shells, generic/emissive batches and four echo skins.
- Native BufferBuilder finalization, vertex counts and buffer sizes for each.
- Actual production Batch.submit through Minecraft's particle group and
  CustomFeatureRenderer for sustained 1-, 5- and 20-beam phases.
- The exact loaded Minecraft, NeoForge, Sodium and Iris versions; active shader
  status and selected Complementary Reimagined pack; screenshots and clean exit.

It uses synthetic beam endpoints/echo poses in an isolated test world. This is
real rendering, not a full survival, multiplayer or performance certification.
Only the private test fixture removes an old test wall to keep the camera clear.
No released effect or gameplay code changes terrain.

The exercised launcher and raw receipts are retained in the current Codex
workspace under `work/phantomism-private/render-crash-fix` and
`outputs/phantomism-render-crash-fix`. The harness uses a copy of the supplied
CurseForge profile and an existing private developer-world seed. It does not open
the user's Survival save, use account tokens, or change the original profile.

On this machine, rerun with a NEW case name (receipts are never overwritten):

    python <workspace>/work/phantomism-private/render-crash-fix/run_client.py fixed-fullpack-shader-99-repeat

The case name controls the original/fixed JAR, shaders and full-pack selection.
For a different machine, adapt the installed runtime/profile/seed paths in the
retained harness before launching. It is not a portable one-click test runner.
