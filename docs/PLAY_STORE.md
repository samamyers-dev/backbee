# Play Store listing

Everything the Play Console asks for, in the order it asks. Copy is written
to the character limits Play enforces.

## Identity

| Field | Value |
|---|---|
| App name (30) | `backbee` |
| Package | `dev.backbee` (permanent after first upload) |
| Category | Music & Audio |
| Tags | Podcasts, Audio player |
| Contact email | myersasam@gmail.com |
| Privacy policy URL | the published copy of [PRIVACY.md](PRIVACY.md) - see "Hosting the privacy policy" |
| Pricing | Paid, one-time purchase |
| Ads | No |
| In-app purchases | No |

## Short description (80)

```
A bookmark for 1,000-episode podcast backlogs. One archive at a time.
```

(70 characters.)

## Full description (4,000)

```
backbee is for the show you keep meaning to start from episode one.

Most podcast apps are inboxes: whatever is newest, from every show, all at
once. backbee is a bookmark in a very long book. You add one archive, it
starts at the beginning, and every time you press play it carries on from
exactly where you left off - in the car, on a walk, in the kitchen - until
one day you finish.

BUILT FOR THE BACKLOG
• Oldest-first, always. The archive is the queue; there is nothing to
  manage.
• Your place is sacred. Position is written every few seconds and the
  moment the car turns off or the headphones drop - never "back to the
  start" after a Bluetooth hiccup.
• Smart rewind. Come back after a minute and it picks up where you were;
  come back after a week and it rewinds enough to remember what was going
  on.
• Auto-advance, auto-download, auto-cleanup. The next ten episodes are
  already on the phone, played ones tidy themselves away, and there are no
  decisions to make while listening.
• Per-show speed, skip-intro and skip-outro, so a 2012 episode with a
  four-minute cold open is still one tap.

KNOWS WHETHER THE ARCHIVE IS REALLY THERE
Many feeds only carry the most recent few hundred episodes. backbee checks
before you commit: it counts what the feed declares, walks the archive
pages, cross-checks the podcast directory, and tells you plainly whether
you are looking at 1,247 episodes or a 300-episode window.

EYES-FREE
The screen matters for three seconds at the start of a session. The real
surfaces are the media notification, the lock screen, Android Auto, and a
home-screen widget with just Resume and Skip.

FINISHING IS AN EVENT
When the last episode ends you get the numbers: hours listened, years of
archive, your average speed, the episodes you starred along the way. Then
the next book from the shelf.

WHAT IT IS NOT
No accounts. No cloud. No sync. No ads. No analytics. Nothing about what
you listen to leaves your phone. A nightly backup of your place goes to a
folder of your choosing, and that is the whole story.

For one listener, one device, and a show worth starting from the beginning.
```

## Graphic assets

| Asset | File | Spec |
|---|---|---|
| Hi-res icon | `docs/play/icon-512.png` | 512×512 PNG, 32-bit, no alpha needed |
| Feature graphic | `docs/play/feature-graphic-1024x500.png` | 1024×500 PNG |
| Phone screenshots | **to be taken** | 2–8, 16:9 or 9:16, each side 320–3840 px |

Both graphics are rendered from the HTML next to them (`icon.html`,
`feature-graphic.html`) with headless Chromium, so they can be regenerated
if the palette moves. The feature graphic falls back to DejaVu where Impact
is not installed; re-render on a machine with Impact for the intended
wordmark.

Screenshots have to come from a phone or emulator running the release
build. Suggested set, in the dark theme:

1. Now - an archive a few hundred episodes in, RESUME prominent.
2. Archive - the year band and the row states.
3. Add show - the completeness check readout with a good verdict.
4. Episode page with the player open.
5. Completion screen.
6. Media notification on the lock screen.

## Data safety form

Answer every question from the position that the developer collects nothing
and the app has no server. Play's definitions: *collected* means transmitted
off the device to the developer or a third party acting for the developer;
*shared* means passed to a third party. Requests the user's device makes
directly to a podcast host or to a public directory API, with nothing
identifying the user beyond the IP address any HTTP request carries, are not
collection under those definitions.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | n/a (nothing collected). Note for your own record: directory calls are HTTPS; feed and audio URLs are whatever the publisher supplies, which may be HTTP - the app allows cleartext for that reason. |
| Do you provide a way for users to request that their data is deleted? | n/a; uninstalling deletes everything, and the policy says so. |
| Independent security review | No |

Data types, for the record, all **not collected**: location, personal info,
financial, health, messages, photos/videos, audio (the app plays audio, it
does not record any), files and docs (the backup folder is written to, not
read from), calendar, contacts, app activity, web browsing, app info and
performance (no crash reporting SDK; Android Vitals in the Play Console is
Google's own collection under Google's terms), device or other IDs.

## Content rating (IARC questionnaire)

Category: **Utility, Productivity, Communication, or Other**. Answer no to
every content question; the app has no content of its own. Does the app
allow users to interact or exchange content: **no**. Share location: **no**.
Purchases of digital goods: **no**. Result should be Everyone / PEGI 3.

## App content declarations

| Declaration | Answer |
|---|---|
| Target audience | 18 and over (simplest; the app is not designed for children) |
| Appeals to children | No |
| News app | No |
| COVID-19 contact tracing | No |
| Government app | No |
| Financial features | None |
| Health | No health features |
| Advertising ID | Not used |
| Foreground service permission | **Yes - `mediaPlayback`.** Describe: "Plays podcast audio with the screen off, controlled from the notification, lock screen, headset and Android Auto." Play asks for a short video of the service in use; a screen recording of starting playback, locking the phone and pausing from the lock screen is enough. |
| Photo and video permissions | Not requested |
| App access (for review) | "No sign-in. To test: Shelf → Add a show → paste any public RSS feed URL, e.g. https://feeds.megaphone.fm/... or search by name → Add → Play." Give the reviewer one specific public feed that passes the completeness check. |

## Android Auto declaration

Only if Auto ships in this release. In App content → **Android Auto**,
declare the app as a media app. Google reviews Auto apps against the
[car app quality guidelines](https://developer.android.com/docs/quality-guidelines/car-app-quality),
which takes longer than the phone review and holds every subsequent update
until it passes. The relevant criteria backbee already meets: a
`MediaBrowserService`, play/pause and skip from the session, title and
artwork metadata on every item, no more than a few browse levels, no
text-heavy screens. Voice search ("play backbee") goes through
`onSearch`; test it on the Desktop Head Unit first.

## Hosting the privacy policy

Play requires a public URL. Two zero-cost options:

1. **GitHub Pages.** Repository → Settings → Pages → Source: *Deploy from a
   branch*, branch `main`, folder `/docs`. The policy then lives at
   `https://samamyers-dev.github.io/backbee/PRIVACY` (Pages renders
   `.md` through Jekyll). Works whether the repository is public or, on a
   paid GitHub plan, private.
2. **A GitHub Gist** with the Markdown, using its public URL.

Either way the store listing field wants the exact URL.

## Pre-launch checklist that is not code

- [ ] Payments profile approved in Play Console (needed before a paid app
      can be published).
- [ ] Developer account identity verification complete.
- [ ] If the account is personal and was created after 13 Nov 2023: a closed
      testing track with ≥12 testers opted in for 14 continuous days, then
      apply for production access.
- [ ] Price set; note the country availability list.
- [ ] Privacy policy URL is live and returns the current text.
- [ ] Foreground-service video uploaded.
- [ ] Screenshots uploaded from the release build.
