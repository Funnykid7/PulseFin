# Release signing

The `release` build type signs with a real keystore when one is configured, and falls back to
debug signing otherwise so local builds keep working without any setup. Nothing keystore-related
is ever committed — `*.keystore`, `*.jks`, and `keystore.properties` are all gitignored.

## One-time setup

1. Generate a keystore (skip if you already have one):

   ```sh
   keytool -genkeypair -v -keystore release.keystore -alias pulsefin \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

   Store `release.keystore` somewhere outside the repo, or anywhere inside it — it's gitignored
   either way. **Back it up.** If you lose it, you can never publish an update under the same
   app signature again (Play Store and most sideload-update flows require a matching signature).

2. Point Gradle at it, either via a `keystore.properties` file at the repo root:

   ```properties
   storeFile=/absolute/or/repo-relative/path/to/release.keystore
   storePassword=...
   keyAlias=pulsefin
   keyPassword=...
   ```

   or via environment variables (handy for CI): `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
   `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. `keystore.properties` takes priority if both are
   present.

3. Build as usual — `./gradlew :app:assembleRelease` now signs with the real key. Without either
   of the above, it silently falls back to debug signing (`app/build.gradle.kts`), which is fine
   for local perf testing but **not** for a GitHub release or Play Store upload.
