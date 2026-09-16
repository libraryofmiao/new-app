# Miao Library CMS

Open `index.html` in a browser or publish the `cms/` directory through the library website hosting.

## Current functions

- Create announcements, events, and advertisements
- Set display order
- Set publication and expiry timestamps
- Add body text, image preview URL, and optional HTML fragment
- Mark content as published or draft
- Preview patron-facing content
- Download a `content.json` feed

## Important deployment note

The current page is a secure UI prototype. It stores drafts in browser `localStorage` and exports JSON. It does not place credentials or write access tokens in browser code.

For production publishing, the page must submit to an authenticated server-side CMS endpoint. The Android app must never read this page directly and must never connect directly to Koha. The intended flow is:

`CMS webpage → authenticated gateway CMS route → content storage → Android gateway content route → Android app`

The Android feed contract is:

```json
{
  "items": [
    {
      "id": "unique-id",
      "type": "announcement",
      "title": "Library update",
      "body": "Message for patrons",
      "image_url": "https://...",
      "html": "",
      "published_at": "2026-09-16T09:00:00+05:30",
      "expires_at": "2026-09-20T18:00:00+05:30",
      "sort_order": 0,
      "published": true
    }
  ]
}
```

The gateway should filter unpublished, future, and expired items before returning the feed. The final gateway URL will be added to `LibraryApi.kt` after the server-side route is implemented and verified.
