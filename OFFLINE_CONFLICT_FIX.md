# Offline Conflict Fix

## Bug fixed

Old issue:

1. User A and User B are online.
2. User B goes offline.
3. User B writes offline.
4. User A writes online.
5. User B reconnects.
6. User B offline content overwrites User A online content.

## Correct behavior now

Offline content is never treated as final document content.
Offline changes are converted into operations and synced through:

```text
POST /api/documents/{documentId}/sync
```

Backend transforms stale offline INSERT operations against operations that already happened online.

Example:

```text
Base content:        Hello
User A online edit:  Hello A-online
User B offline edit: Hello B-offline
Final content:       Hello A-online B-offline
```

## Safety rule

If the offline change is destructive, like DELETE or REPLACE, and the server changed while the user was offline, backend does not allow that stale delete to remove online users' work.

Frontend handles this by appending an offline draft block to the latest server content instead of overwriting it.

## Backend files changed

- `OperationService.java`
  - Uses each offline operation's original baseVersion.
  - Rejects stale destructive offline DELETE when server changed.

- `DocumentSyncController.java`
  - Broadcasts latest document snapshot after HTTP `/sync` so online users see the offline merge without refresh.

- `GlobalExceptionHandler.java`
  - Missing static assets now return 404 instead of generic 500.

- `DocumentOperation.java`
  - Added index on `{documentId, serverVersion}` for faster operation history lookup.
