# backbee privacy policy

_Last updated: 9 September 2026_

backbee is a podcast player for listening through one show's archive at a
time. It is built to work without an account, without a server of ours, and
without telling anyone what you listen to.

## What backbee collects

**Nothing.** backbee has no analytics, no advertising, no crash-reporting
SDK, and no account system. We do not receive any data from the app, and
there is no server of ours for it to send anything to.

## What stays on your device

Everything the app knows lives in its private storage on your phone:

- the shows you have added and their episode lists;
- your listening position in each episode, and which episodes you have
  marked played, starred, or noted;
- downloaded episode audio;
- your settings.

Uninstalling the app deletes all of it. If you have Android's backup enabled,
Android may include the app's database (not the downloaded audio) in your
Google account's device backup, under Google's own privacy terms. If you
choose a backup folder in Settings, the app writes a nightly copy of its
database to that folder and to nowhere else. Restoring reads only the single
file you pick.

## Who your device talks to

To do its job the app connects, from your device, to:

- **The podcast's own servers**, to fetch the feed you added and to stream or
  download episode audio and artwork. Those servers see your IP address and
  the app's user agent (`backbee/1.0`), as any podcast player's requests
  would. Their handling of that is governed by the publisher's own policy.
- **Apple's iTunes Search API**, only when you use the search box to find a
  show by name. Your search term is sent to Apple. See Apple's privacy
  policy.
- **Podcast Index** (podcastindex.org), to check how many episodes a show
  really has and, when a feed only offers its most recent episodes, to fetch
  the older ones. The feed's URL is sent; nothing about you is.

No request is made to any other service. The app makes no requests at all
about which episode you are listening to or how far you have got.

## Permissions

- **Internet / network state**: fetching feeds and audio; deciding whether a
  download should wait for Wi-Fi.
- **Notifications** (Android 13+): the playback controls in the notification
  shade and on the lock screen. Declining it does not affect anything else.
- **Foreground service (media playback)** and **wake lock**: keeping audio
  playing with the screen off.
- **Run at boot**: re-scheduling the nightly backup and the daily feed check
  after a restart.

The app does not request access to your location, contacts, microphone,
camera, photos, or files beyond the single backup folder you may choose.

## Children

backbee is not directed at children and has no content of its own; it plays
whatever feeds you add.

## Changes

If this policy changes, the new version will be published at the same
address, with the date above updated.

## Contact

Questions about this policy: **myersasam@gmail.com**.
