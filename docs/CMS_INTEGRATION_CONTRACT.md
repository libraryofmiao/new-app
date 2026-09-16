# Library CMS integration contract

This document defines the read-only content contract consumed by the Android patron app from the library gateway.

## Scope

The Home screen may display library-controlled announcements, events, advertisements, HTML notices, HTTPS image previews, and future institution logos. The Android app consumes published content through the gateway; it does not connect directly to the CMS or Koha.

## Endpoint

`GET https://api.miaolibrary.in/cms/content`

The endpoint is public/read-only from the Android client's perspective. No CMS administrator credential is embedded in the app.

## Response shape

The gateway returns a JSON object containing an `items` array. Each item may contain:

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
  "sort_order": 1,
  "notification_enabled": false,
  "logo_url": "https://example.invalid/logo.jpg"
}
```

## Supported content types

- `announcement` (also accepted by the parser as `notice` or `notification`)
- `event`
- `advertisement` (also accepted as `ad` or `banner`)
- Unknown types are retained as `UNKNOWN` so the client can fail safely.

## Publishing rules

The gateway should return only content that is currently published, within its publication window, and permitted for the mobile app. The Android client does not implement CMS administration, publishing, scheduling, or ordering controls.

## Rendering rules

- HTML is rendered with Android's built-in HTML parser.
- Remote images/logos are loaded only from HTTPS URLs.
- The gateway must sanitize HTML before returning it to the client.
- The gateway should validate or allowlist remote image hosts where practical.

## Notifications

`notification_enabled` is carried by the feed for future gateway-controlled announcement notifications. Actual reliable announcement push delivery requires server-side notification infrastructure and is not implemented by this client contract alone.

## Current implementation status

The Android client now consumes the live `/cms/content` gateway endpoint and renders announcements/events/advertisements, optional HTML, and HTTPS image previews on the Home screen. The gateway remains responsible for publication filtering and server-side sanitization.
