# Shito Pool Hacker

Android prototype for **visual aiming guides only** in 8 Ball Pool. It does not tap, shoot, move the cue, or play automatically.

## Current prototype
- Opens 8 Ball Pool if the official app is installed (`com.miniclip.eightballpool`).
- Requests Android overlay permission and draws non-interactive guide lines above other apps.
- Lets the user start and stop the overlay.

## Limitations
The guide lines are fixed demo lines. They do **not** detect the table or balls and do not calculate collisions, pockets, or cushion rebounds yet. Automatic detection is the next development stage and requires real-device testing across table colors, screen sizes, and game versions.

## Build
Open this repository in Android Studio, allow Gradle sync, then run the `app` configuration on an Android device (minimum Android 8.0).

The app requires the user's Android "Display over other apps" permission. No game files are modified and no game protection is bypassed.
