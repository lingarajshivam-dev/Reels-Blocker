# Invisible Blocker (Android source project)

This is a **source project prototype**, not a compiled APK. It targets Android 8.0+ and watches accessibility click/selection events from YouTube and Instagram. When it sees a click event whose label is exactly `Shorts` or `Reels`, it requests Back.

## Important limitations
- This does not literally cancel a tap. Android Accessibility Services cannot cancel another app's click; this prototype navigates Back immediately after the click is reported.
- YouTube/Instagram labels, UI structure, and language vary. The prototype may not detect all entry points, and app updates can break it.
- It does not block Shorts/Reels opened from links, search, profile pages, or other routes.
- Android can stop background services; uninterrupted operation is not guaranteed.
- Use only if you understand that an Accessibility Service can observe UI events. This prototype does not transmit data or request internet permission.

## Build
Open this folder in Android Studio (current stable version), let Gradle sync, then select Build → Build Bundle(s) / APK(s) → Build APK(s). The debug APK is usually created at `app/build/outputs/apk/debug/app-debug.apk`.

## Install
Install the APK, then enable the Accessibility Service in Android Settings. Because the app has no launcher activity/icon, there is no in-app setup screen.
