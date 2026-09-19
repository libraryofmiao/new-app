# Miao Library iPhone App

Native SwiftUI iOS companion for the existing Android app.

## Separation
The iOS project lives entirely under `ios/MiaoLibrary/`. Existing Android files under `app/`, Gradle files, and Android resources are not modified by this project.

## Gateway
The app uses the existing read-only gateway:
- https://api.miaolibrary.in

Authentication is through the existing Koha OPAC-backed gateway session. The bearer token is stored in the iOS Keychain.

## Current implementation
- Login/session persistence
- Member profile
- Home and account screens
- Current issued books
- Per-patron local issued-book cache
- Daily refresh after 5 PM IST
- Manual issued-book refresh
- Previous Issues gateway call
- Native iOS tab navigation
- Foundation for read-only catalogue integration

## Building
Open the `Package.swift` in Xcode on macOS. An actual iPhone installation requires Apple code signing and an Apple Developer provisioning setup. The repository does not contain signing credentials.
