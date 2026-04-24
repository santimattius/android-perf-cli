# perf-cli distribution

## 1. GitHub Releases (official JAR)

1. Bump the version in [`gradle.properties`](gradle.properties) (`version=…`).
2. Create and push a semver tag:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```
3. The [`.github/workflows/release.yml`](.github/workflows/release.yml) workflow (**Release & distribution**) builds the fat JAR, publishes a **GitHub Release** with downloadable assets, bumps the Homebrew formula, and (best-effort) attaches a **Linux GraalVM native** binary to the same release. Release assets include:
   - `perf-cli-<version>.jar`
   - `checksums-sha256.txt`
   - `perf-<version>-linux-amd64` and `perf-<version>-linux-amd64.sha256` when the native build succeeds (the native job uses `continue-on-error: true` so a failing native image does not block the JAR or Homebrew).

User install (replace `OWNER`, `REPO`, and the version):

```bash
VERSION=0.1.0
curl -fsSL -o perf-cli.jar \
  "https://github.com/OWNER/REPO/releases/download/v${VERSION}/perf-cli-${VERSION}.jar"
echo 'alias perf="java -jar '"$(pwd)/perf-cli.jar"'"' >> ~/.zshrc
```

Verify the SHA256 against `checksums-sha256.txt` from the release.

If the release includes a **Linux native** asset (same tag workflow, when GraalVM build succeeds):

```bash
VERSION=0.1.0
curl -fsSL -O "https://github.com/OWNER/REPO/releases/download/v${VERSION}/perf-${VERSION}-linux-amd64"
curl -fsSL -O "https://github.com/OWNER/REPO/releases/download/v${VERSION}/perf-${VERSION}-linux-amd64.sha256"
sha256sum -c "perf-${VERSION}-linux-amd64.sha256"
chmod +x "perf-${VERSION}-linux-amd64"
./perf-${VERSION}-linux-amd64 --help
```

---

## 2. Homebrew (auto-bumped on every tag, no extra repo)

The release workflow keeps the formula at [`Formula/android-perf-cli.rb`](Formula/android-perf-cli.rb) **inside this same repository**. On every `v*` tag the workflow:

1. Builds the JAR and publishes the GitHub Release.
2. Re-renders `Formula/android-perf-cli.rb` with the new `url`, `version`, and `sha256`.
3. Commits and pushes the bump to the **default branch** with the bot account `github-actions[bot]`.

No tap repository, no PATs, no extra secrets — only `permissions: contents: write` (already set in the workflow).

### User install

`brew tap` accepts an explicit Git URL, so any repository with a top-level `Formula/` folder works as a tap:

```bash
brew tap OWNER/android-perf-cli https://github.com/OWNER/android-perf-cli
brew install android-perf-cli
perf --help
```

The Homebrew **package** is `android-perf-cli`; the installed **binary** is `perf` (CLI entrypoint).

To upgrade after a new tag:

```bash
brew update && brew upgrade android-perf-cli
```

### Branch protection note

If the default branch has protection rules that **require pull requests**, the workflow's direct push will be rejected. Either:

- Allow `github-actions[bot]` to bypass protection on the default branch, **or**
- Switch the workflow to open a PR instead of pushing directly (requires a PAT with `contents: write` and `pull-requests: write`).

### Local install (from a clone, without `brew tap`)

```bash
brew install --formula ./Formula/android-perf-cli.rb
```

The committed file is the source of truth — after the first release, `OWNER`, version, and sha256 are already correct.

---

## 3. GraalVM Native Image (`perf` binary)

**On every `v*` tag**, the same [`.github/workflows/release.yml`](.github/workflows/release.yml) runs a follow-up job that builds a Linux native image and uploads **`perf-<version>-linux-amd64`** (plus a `.sha256` file) to the GitHub Release for direct **cURL** installs. If that job fails, the JAR release and Homebrew bump still succeed.

Locally you need a **GraalVM JDK** with the `native-image` component:

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
