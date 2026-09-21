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

## Launch sequence for a personal Play account

A personal developer account created after 13 November 2023 cannot publish
to production until the app has run a closed test with **at least 12
testers opted in for 14 consecutive days**. Everything below is ordered
around that clock.

1. **Day 0 - account and app.** Play Console account, identity
   verification, payments profile (paid apps need it before pricing can be
   set). Create the app, complete the store listing and every App content
   declaration from [PLAY_STORE.md](PLAY_STORE.md). Enable Play App Signing.
2. **Day 0 - first bundle.** Tag `v1.0.0`. Upload the bundle from the
   Release workflow to the **Internal testing** track and install it on your
   own phone from the Play link, so the very first install is the Play-built,
   Play-signed artifact rather than a sideload.
3. **Day 1 - closed test.** Create a **Closed testing** track, add an email
   list of testers (12 is the floor; recruit 15-20 so drop-outs do not reset
   anything), promote the internal build to it, and send the opt-in link.
   Testers must stay opted in and keep the app installed; the 14 days count
   only while the tester count stays at or above 12.
4. **Days 1-14 - iterate.** Every fix is a new tag and a new closed-track
   release; that does not restart the clock. Read Android Vitals daily for
   crashes and ANRs, and the closed testers' feedback in the Console.
5. **Day 15 - apply for production access.** The Console asks what was
   tested and what changed. Answer from the closed-track release notes.
   Approval typically takes a few days.
6. **Production.** Promote the closed build (or tag a fresh one) to
   production. If Android Auto is declared, expect the car-app quality review
   on top of the standard one; it can take a week or more and it holds every
   later update until it passes, so ship the Auto build first and let it
   clear before planning 1.0.1.

Total: roughly three to four weeks from account creation to a live paid
listing, most of it waiting.

## Known gaps to close before or shortly after 1.0

Findings from the pre-release audits that were **not** fixed in code, in
priority order. None blocks a closed test; the first two are worth doing
before production.

- **Restore from backup is untested on a device.** Settings → "Restore from
  a backup file" reads a `.bak`, checks its integrity and tables, shows what
  is in it, then swaps the database and restarts the process. Walk through it
  once on a phone (back up, mark a few episodes, restore, confirm they are
  back) before production.
- **A dead episode pins Resume.** The player now skips an episode whose
  audio is gone, but the resume target is the lowest unplayed episode with
  an enclosure, so a cold Resume from the widget, Auto or a headset lands on
  it again until it is marked played by hand. Fix needs a schema change
  (mark episodes unplayable) and therefore a Room migration.
- **Assistant's "play X on backbee" has no entry point.** The browse-side
  search Android Auto uses is implemented (`onSearch` / `onGetSearchResult`),
  but the manifest has no `MEDIA_PLAY_FROM_SEARCH` intent filter, so a spoken
  request never reaches the app. Adding the filter also means handling the
  intent in `MainActivity` and playing the best match; an empty filter would
  launch the app and do nothing, which is worse. Lint flags this as
  `MissingIntentFilterForMediaSearch`. Worth closing before the Android Auto
  review, which expects voice support.

- **Auto-play on Bluetooth on Android 12+.** Starting playback from an audio
  device callback is a background start with no foreground-service
  exemption. Media3 catches the exception and playback carries on
  un-foregrounded until the OS kills the process. The setting is off by
  default; consider hiding it on API 31+ or documenting it as best-effort.
- **Podcast Index secret ships inside the APK.** Needed on-device to sign
  requests, so it is extractable. It is a free-tier key: monitor for abuse
  and rotate through an app update, or front it with a tiny proxy later.
- **Only `2.json` is committed for the Room schema; `1.json` never was.** The
  1→2 migration cannot be covered by a `MigrationTestHelper` test. Nothing
  has shipped, so collapsing to version 1 before the first upload is an
  option; otherwise regenerate `1.json` from commit `bba21c6^`.
- **Screenshots.** Play needs at least two phone screenshots from a real
  device or emulator; the CI-rendered component images are not screens.
- **Accessibility.** Episode-row state glyphs carry no `stateDescription`,
  the scan bar has no accessibility seek action, and a few Settings controls
  are under 48 dp. TalkBack users can use the app but not comfortably.
- **English only.** Roughly 300 UI strings live in Kotlin rather than
  resources. Fine for launch; a deliberate choice to revisit if a second
  language is ever wanted.
- **No UI tests for playback.** `PositionWriter`, `PlaybackCoordinator` and
  the download worker have no automated coverage; the device checklist
  above is what stands in for it.
