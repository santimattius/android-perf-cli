# perf-cli distribution

## 1. GitHub Releases (official JAR)

1. Bump the version in [`gradle.properties`](gradle.properties) (`version=…`).
2. Create and push a semver tag:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
3. The [`.github/workflows/release.yml`](.github/workflows/release.yml) workflow builds the fat JAR and publishes a **GitHub Release** with:
   - `perf-cli-<version>.jar`
   - `checksums-sha256.txt`

User install (replace `OWNER`, `REPO`, and the version):

```bash
VERSION=0.1.0
curl -fsSL -o perf-cli.jar \
  "https://github.com/OWNER/REPO/releases/download/v${VERSION}/perf-cli-${VERSION}.jar"
echo 'alias perf="java -jar '"$(pwd)/perf-cli.jar"'"' >> ~/.zshrc
```

Verify the SHA256 against `checksums-sha256.txt` from the release.

---

## 2. Homebrew (sample formula)

[`packaging/homebrew/perf-cli.rb`](packaging/homebrew/perf-cli.rb) contains a template. Before publishing:

- Replace `YOUR_GITHUB_ORG` with the real org or user.
- Update `url`, `version`, and `sha256` (copy the hash from the release).

Local install from a clone:

```bash
brew install --formula ./packaging/homebrew/perf-cli.rb
```

For your own **tap**: copy the formula to `homebrew-tap/Formula/perf-cli.rb` and document `brew tap owner/tap`.

---

## 3. GraalVM Native Image (`perf` binary)

Requires a **GraalVM JDK** with the `native-image` component (or CI using [`.github/workflows/native-image.yml`](.github/workflows/native-image.yml), run manually from the Actions tab).

```bash
./gradlew :app:nativeCompile
```

The binary is usually produced under `app/build/native/nativeCompile/` (executable name: `perf`; see `imageName` in `app/build.gradle.kts`).

Kotlin, kotlinx-serialization, and other dependencies may need **extra metadata** for native-image; if the build fails, keep using the fat JAR until reachability metadata is adjusted.

---

## 4. Android SDK on clean machines

This is not part of the JAR packaging, but the CLI can install **platform-tools** (adb):

```bash
perf sdk install
```

See [INSTALL.md](INSTALL.md#install-the-sdk-from-perf-cli-without-android-studio).
