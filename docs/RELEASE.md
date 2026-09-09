# Releasing to Google Play

The pipeline is: **tag → signed App Bundle → (optionally) straight onto a Play
track.** Everything sensitive lives in GitHub secrets; nothing signing-related
is in the repository.

## One-time setup

### 1. The upload keystore

Play App Signing holds the real app signing key. What you generate here is the
*upload* key, which only proves a bundle came from you. Losing it is
recoverable (Play support can reset an upload key); leaking it is not
catastrophic but still means resetting it. Keep it out of the repo and out of
chat logs.

```bash
keytool -genkeypair -v \
  -keystore backbee-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=backbee, O=backbee, C=US"
```

Use one strong password for both the store and the key when prompted (the
build falls back to the store password for the key if only one is set).

Store `backbee-upload.jks` somewhere backed up and private (a password
manager's file attachment is ideal). Then:

```bash
base64 -w0 backbee-upload.jks > backbee-upload.jks.b64
```

### 2. GitHub secrets

Repository → Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `BACKBEE_KEYSTORE_BASE64` | Contents of `backbee-upload.jks.b64` |
| `BACKBEE_KEYSTORE_PASSWORD` | The keystore password |
| `BACKBEE_KEY_ALIAS` | `upload` (or whatever you passed to `-alias`) |
| `BACKBEE_KEY_PASSWORD` | The key password (omit if the same as the keystore password) |
| `PODCAST_INDEX_KEY` / `PODCAST_INDEX_SECRET` | Optional. From https://api.podcastindex.org. Without them the Archive completeness check has no second opinion, and truncated feeds cannot be completed from the directory. |
| `PLAY_SERVICE_ACCOUNT_JSON` | Optional. Enables the automatic upload to a Play track (step 4). |

Delete the `.b64` file once the secret is saved.

### 3. Play Console

1. Create the app: **backbee**, app, paid, category Music & Audio.
2. **App integrity → App signing**: accept Play App Signing. On the first
   upload, Play derives the app signing key itself; the bundle you upload is
   signed with the upload key from step 1.
3. **Monetise → Products → App pricing**: set the price. A paid app needs a
   payments profile first (Setup → Payments profile). Note that a paid app
   can later be made free but not the reverse.
4. Fill in the store listing from [PLAY_STORE.md](PLAY_STORE.md): text,
   icon, feature graphic, screenshots, privacy policy URL.
5. **Policy → App content**: privacy policy, ads (none), data safety (answers
   in PLAY_STORE.md), content rating questionnaire, target audience (18+ or
   13+; the app has no content of its own), news app (no), COVID (no),
   government app (no), financial features (none).
6. If the account is a *personal* one created after 13 November 2023, Play
   requires a closed test with at least 12 testers opted in for 14
   continuous days before it will grant production access. Plan for that on
   the calendar: it is the long pole.

### 4. Optional: automatic upload to a track

In Google Cloud Console, create a service account, grant it nothing at the
project level, download a JSON key. In Play Console → Users and permissions,
invite that service account's email with **Release to testing tracks** (and
production, if you want tags to go all the way) on this app. Put the JSON in
the `PLAY_SERVICE_ACCOUNT_JSON` secret. The workflow uploads to the
`internal` track by default; `workflow_dispatch` lets you pick another.

Without this secret the workflow still builds and signs; you download the
`.aab` from the run's artifacts and upload it in the Play Console by hand.

## Each release

```bash
git checkout main && git pull
git tag v1.0.0          # semantic version; the workflow strips the v
git push origin v1.0.0
```

The **Release** workflow then:

1. Runs the JVM and app unit tests.
2. Computes `versionCode = 100 + run number`, `versionName` from the tag.
3. Builds `bundleRelease` and `assembleRelease` signed with the upload key,
   with the Podcast Index credentials baked in from secrets.
4. Verifies the signatures with `jarsigner` and `apksigner`.
5. Uploads `backbee-<version>.aab`, `.apk` and `mapping-<version>.txt` as a
   workflow artifact (90 days, sign-in required).
6. If `PLAY_SERVICE_ACCOUNT_JSON` is set, pushes the bundle to the chosen
   track with the mapping file attached.

Play needs the `mapping.txt` to deobfuscate crash reports in Android Vitals.
It is embedded in the bundle by the Android Gradle Plugin already; the
separate copy is for uploading by hand if a bundle is ever uploaded that way.

## Versioning rules

- `versionCode` must only ever go up across uploads. It comes from the
  Release workflow's run number, so never build a Play upload anywhere else.
  If you ever must (a manual upload with a higher number, or the workflow
  file is renamed and its counter resets), raise `VERSION_CODE_BASE` in
  `release.yml` above the highest number Play has seen.
- The application id `dev.backbee` is permanent once the first bundle is
  uploaded. The debug build uses `dev.backbee.debug` and installs alongside.
- Room schema changes must ship with a `Migration` (see `BackbeeDatabase`)
  and the new `app/schemas/dev.backbee.data.db.BackbeeDatabase/N.json`
  committed. The database holds every listening position; there is no
  destructive-migration fallback and there must never be one.

## Local signed build

For a signed build on a developer machine, put in `~/.gradle/gradle.properties`:

```properties
backbeeKeystorePath=/absolute/path/backbee-upload.jks
backbeeKeystorePassword=...
backbeeKeyAlias=upload
backbeeKeyPassword=...
```

then `./gradlew :app:bundleRelease -PversionCode=NNN -PversionName=1.0.0`.
Without these properties `bundleRelease` still runs and produces an unsigned
bundle, which is what the ordinary CI build does on every push to prove R8
and resource shrinking work.

## Before tagging 1.0.0

- [ ] CI is green on `main`, including the unsigned release build step.
- [ ] Installed the release APK from the CI artifact on a real phone and
      played through at least one episode boundary (auto-advance), one
      Bluetooth disconnect, one force-stop and relaunch, one reboot.
- [ ] Android Auto tested on the Desktop Head Unit or a car
      (docs/ANDROID_AUTO.md) if Auto is in this release.
- [ ] Privacy policy is live at the URL in the store listing.
- [ ] Screenshots taken from the release build on a phone (Play wants at
      least two, 16:9 or 9:16, 320–3840 px on each side).
