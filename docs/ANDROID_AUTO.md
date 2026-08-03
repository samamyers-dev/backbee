# Android Auto

## The gotcha that costs an afternoon

Android Auto only lists media apps that were installed from Play. A sideloaded
personal build will not appear at all — not greyed out, not erroring, simply
absent.

To fix it on your own phone:

1. Open the **Android Auto** app (on newer Android it lives under
   Settings → Connected devices → Connection preferences → Android Auto).
2. Scroll to the bottom and tap the **Version** entry ten times to unlock
   developer settings.
3. Open the overflow menu → **Developer settings**.
4. Enable **Unknown sources**.
5. Reconnect. backbee should now appear in the media app list.

This has to be done once per device, and it sometimes resets after an Android
Auto update.

## What the car sees

Three entries, and no more. A screen at arm's length in a moving vehicle is not
a place to browse 1,247 episodes:

- **Resume** — the bookmark. The same episode and the same smart-resume rewind
  the phone would give you.
- **Next up** — the following 25 episodes in archive order.
- **Starred** — everything starred, oldest first.

Playing anything from any of these goes through `PlaybackCoordinator`, exactly
like the phone's Play button, so per-show speed, skip-intro and the rolling
queue window all behave identically in the car.

## Implementation notes

`PlaybackService` extends `MediaLibraryService` rather than `MediaSessionService`
— the library callbacks are what Auto browses. The manifest declares both the
Media3 action and the legacy `android.media.browse.MediaBrowserService` action,
plus `@xml/automotive_app_desc` declaring media support.

`AutoLibraryCallback.onAddMediaItems` matters more than it looks: Auto hands back
a `MediaItem` carrying only the media id it was given, with no URI. Without
resolving those back to real episodes, tapping anything in the car does nothing.

## Testing without a car

The Desktop Head Unit (DHU) in the Android SDK will do:

```bash
adb forward tcp:5277 tcp:5277
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```

Enable **Start head unit server** in Android Auto's developer settings first.
