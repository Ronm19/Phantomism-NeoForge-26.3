# Phantomism native release regressions

These developer tests exercise the actual Minecraft/NeoForge runtime in a separate
world. They are deliberately outside `src/main` and are excluded from the released
mod JAR. They do not edit approved textures, player saves or gameplay configuration.

Prerequisites: Java 25 JDK, Python 3, and the project's dependencies already resolved
by the normal Gradle/IDE setup. No new dependencies or test framework are required.

From the project root on Windows:

    python verification/run.py --java-home "C:/path/to/your/jdk-25"
    python verification/run.py --java-home "C:/path/to/your/jdk-25" --reload

`JAVA_HOME` or Java/Javac on PATH may be used instead of `--java-home`.
The second command verifies the first run's 58-species saved ownership/baby/order
data in a fresh JVM. Use the same output directory for the pair. Reports and private
worlds are written under `build/release-verification`. Original Survival saves are
never opened. Failures are detected from assertion receipts as well as process exit.

Coverage: 58-species registration/ownership/NBT, allied-pet retaliation after losing
an attacker, sustained defensive validity and direct selection priority, protected
allies/other owners/Clear Target, staff-free Follow/Stay and bed registration, moving
owner/obstacles, mounted cadence/recovery, config translations, live Staff timer
bindings for all current manual entries, Solar combat, advancement rarity frames,
current 16-block Codex proximity/Creator secrecy rules and native projectile safety.

The Staff regression keeps the AI/Staff and mounted intervals distinct. It checks
that manual commands respect native recovery, an allowed mounted shot never lowers
that recovery, duplicate rider requests fail, and dismounting cannot reset it. Each
backing timer is resolved and perturbed in an isolated developer entity; no external
historical field manifest is needed. An unbound manual timer fails the test.

These controlled native tests are not human gameplay, an FPS/TPS benchmark, or a
substitute for actual-client/dedicated-server compatibility checks. The final release
report includes those separate runtime and profiling results. Ordinary Gradle
`test` remains NO-SOURCE; run this explicit command for the native regressions.

If Windows reports a Java loopback/socket error, pass `--socket-dir` with a short writable folder. The verifier otherwise uses a short folder in your user profile. Its developer classpath export disables configuration caching for that invocation only.
