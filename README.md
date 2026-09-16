# Miao Library Patron Android App

Android patron app for Miao Library.

## Current features

- Secure patron login through the Miao Library gateway
- Encrypted local session token storage using Android Keystore
- My Books view for currently issued items
- Issue History view for previous issues
- Read-only catalogue search
- Book details with bibliographic information and holdings/copy details
- Bundled Miao Library logo
- Android back navigation between catalogue, My Books, and book details

## Architecture

The Android app communicates only with the gateway:

`https://api.miaolibrary.in`

It does not call Koha directly. The gateway communicates with the Koha OPAC at:

`https://opac.miaolibrary.in`

## Project structure

- `app/src/main/java/in/miaolibrary/patron/MainActivity.kt` — screens and navigation
- `app/src/main/java/in/miaolibrary/patron/LibraryApi.kt` — gateway API client
- `app/src/main/java/in/miaolibrary/patron/SessionStore.kt` — session storage support
- `app/src/main/assets/miao_logo_base64.txt` — bundled library logo

## Build

Open the project in Android Studio and run the `app` configuration on an Android 8.0+ device or emulator.

The project uses Kotlin, Android Views, compile SDK 36, minimum SDK 26, and Java 17 compatibility.
