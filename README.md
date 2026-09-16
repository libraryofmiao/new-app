# Miao Library Patron Android App

Android patron app for Miao Library.

## Current features

- Secure patron login through the Miao Library gateway
- Encrypted local session token storage using Android Keystore
- Dedicated Home dashboard
- CMS-powered announcements, events, advertisements, HTML-formatted notices, and HTTPS image previews
- My Books view for currently issued items
- Issue History view for previous issues
- Read-only catalogue search
- Book details with bibliographic information and holdings/copy details
- Bundled Miao Library logo
- Android back navigation between catalogue, My Books, and book details
- About & Privacy screen
- Android notification permission setup and closed-app due-date reminder scheduling
- Reproducible GitHub Actions debug build

## Architecture

The Android app communicates only with the gateway:

`https://api.miaolibrary.in`

It does not call Koha directly. The gateway communicates with the Koha OPAC at:

`https://opac.miaolibrary.in`

The patron app does not contain Koha or CMS administrator credentials.

## CMS integration

The Home screen consumes the read-only gateway feed at:

`GET https://api.miaolibrary.in/cms/content`

Only published, currently valid CMS content should be returned by the gateway. The Android client supports announcement, event, and advertisement content and safely treats unknown types as generic updates.

HTML is rendered using Android's built-in HTML parser, and remote media is accepted only from HTTPS URLs. Gateway-side HTML sanitization remains a production requirement.

## Project structure

- `app/src/main/java/in/miaolibrary/patron/MainActivity.kt` — screens and navigation
- `app/src/main/java/in/miaolibrary/patron/AboutActivity.kt` — About, privacy, and support information
- `app/src/main/java/in/miaolibrary/patron/LibraryApi.kt` — gateway API client
- `app/src/main/java/in/miaolibrary/patron/LibraryContent.kt` — CMS content model and parser
- `app/src/main/java/in/miaolibrary/patron/LibraryUpdatesView.kt` — patron-facing CMS renderer
- `app/src/main/java/in/miaolibrary/patron/DueDateReminder.kt` — local closed-app due-date reminders
- `app/src/main/java/in/miaolibrary/patron/MiaoLibraryApplication.kt` — notification channel setup
- `app/src/main/assets/miao_logo_base64.txt` — bundled library logo
- `.github/workflows/android-build.yml` — automated debug build

## Notifications

The app schedules a local reminder for each parsed checkout due date when the account is refreshed. The Android alarm can fire while the app is closed. This is a client-side reminder mechanism; reliable server-side push delivery and CMS-triggered push notifications still require the gateway/notification infrastructure.

## Build

GitHub Actions builds the debug APK with JDK 17 and Gradle 9.4. The workflow uploads `app-debug.apk` as a build artifact.

The project uses Kotlin, Android Views, compile SDK 36, minimum SDK 26, and Java 17 compatibility.
