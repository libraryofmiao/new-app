# Library CMS integration contract

This document defines the read-only content contract expected by the Android app when the library gateway exposes its CMS feed.

## Scope

The Home screen may display library-controlled announcements, events, advertisements, HTML notices, JPG/image previews, and additional institution logos. The Android app must only consume published content through the gateway; it must not connect directly to the CMS or Koha.

## Response shape

The gateway feed should return a JSON object containing an `items` or `content` array. Each item may contain:

```json
{
  "id": "unique-content-id",
  "type": "announcement",
  "title": "Library notice",
  "body": "Plain-text description",
  "image_url": "https://example.invalid/image.jpg",
  "html": "<p>Optional formatted content</p>",
  "published_at": "2026-09-16T09:00:00+05:30",
  "expires_at": "2026-09-30T23:59:00+05:30",
  "sort_order": 1
}
```

## Supported content types

- `announcement` (also accepted by the parser as `notice` or `notification`)
- `event`
- `advertisement` (also accepted as `ad` or `banner`)
- Unknown types are retained as `UNKNOWN` so the client can fail safely.

## Publishing rules

The gateway should return only content that is currently published, within its publication window, and permitted for the mobile app. The Android client should not implement CMS administration, publishing, scheduling, or ordering controls.

## Security requirements

- The Android app must call the gateway over HTTPS.
- The gateway should validate and sanitize HTML before returning it.
- Image URLs should use HTTPS and be allowlisted or proxied by the gateway where practical.
- No Koha credentials, CMS credentials, or administrative tokens may be embedded in the Android app.
- The feed should be read-only from the Android client's perspective.

## Current implementation status

`LibraryContent.kt` contains the parser and models, and `LibraryUpdatesView.kt` renders a safe empty state until a real gateway CMS feed is available. No CMS endpoint is hard-coded because the gateway route has not yet been finalized.
