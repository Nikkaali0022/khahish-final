# Offline-First Mode and Save to Device — Approved Product Scope

Status: Approved requirements; implementation and device verification are still pending.
Target: `main` (do not merge until the product owner has finished adding requirements and explicitly approves the final merge).

## Product goals

Khahish should reopen quickly into the last usable screen and show locally available conversations, messages, profile names, and avatars immediately. Network refreshes must happen in the background and must not block the UI.

## Offline-first data and UI

- Persist conversation metadata, message history already fetched by the user, profile names/usernames, avatar references, and last-opened navigation/chat state locally.
- Use a durable local database (Room or an equivalent repository-compatible store) as the UI's fast read source; keep existing Firebase/Firestore as the remote source of truth.
- Verify/configure Firestore offline persistence without introducing conflicting multiple Firestore instances or changing existing listeners/security behavior.
- Cache avatars and media on disk with bounded cache management; do not depend on memory-only image cache.
- Display cached content immediately after cold start/reopen, then reconcile updates in the background.
- Clearly distinguish cached/available offline data from data that has never been downloaded.

## Offline message outbox

- Sending while offline must create a durable local outgoing message and show it immediately in the conversation with a "Waiting to send" state.
- Keep the outbox across app process death and device restart.
- Automatically retry when connectivity returns, using WorkManager or an equivalent durable scheduler.
- Use stable client-generated message IDs/idempotency to prevent duplicate remote messages during retries.
- Reconcile pending, sent, delivered, and seen states without changing the existing receipt semantics.
- Preserve message order and avoid dropping messages on transient failures.

## Offline media and Stories queue

- Allow composing photo/video messages and photo/video/text/voice Stories while offline where the content is available locally.
- Persist pending media files and their metadata safely; show queued/uploading/sent/failed states and retry/cancel controls.
- Upload/send automatically when connectivity returns; preserve audience/privacy settings, captions, mentions, and recipient/chat association.
- Keep upload retries idempotent and clean up temporary local files only after confirmed success or explicit cancellation.
- Do not imply calls or remote delivery work without a network connection.

## Save to Device for sent and received media

- Add a "Save to Device" item to the existing message three-dot menu, alongside current actions such as Forward and Share.
- Show it for both outgoing and incoming attachments: images, videos, documents, and other downloadable files.
- Save images/videos to the appropriate user-visible Gallery/Photos collection using Android MediaStore; save documents and other files to suitable Downloads/Documents locations using scoped-storage-safe APIs.
- Use Android's Storage Access Framework where a user-selected destination is more appropriate.
- Show progress, success/failure feedback, and a useful duplicate/already-saved response.
- Handle unavailable/offline remote media gracefully; if the file is already cached locally, allow saving from that copy.
- Preserve the existing message menu actions and permissions behavior.

## Non-regression constraints

- Do not remove or redesign existing screens, activities, navigation, chat/call/story flows, notifications, admin tools, privacy controls, or Firebase/Agora configuration.
- Do not change existing delivery/read receipt semantics, message ordering, story audience rules, or media upload behavior except where required for the durable offline queue.
- Keep Firebase Spark compatibility; do not add a requirement for Blaze/pay-as-you-go services.
- Keep minSdk 24 and current Kotlin/Compose/Java toolchain unless a separately approved change is necessary.

## Required verification before merge

1. Build and unit tests pass in GitHub Actions.
2. Verify cached chats, names, and avatars appear immediately after force-stop/reopen and after device restart.
3. Verify airplane-mode message compose/send queues durably, then sends once connectivity returns (no duplicates).
4. Verify queued media and Stories survive app/process restart and upload once online.
5. Verify save-to-device for sent and received image, video, PDF/document, and a generic file on supported Android versions.
6. Verify existing chat, calls, stories, notifications, receipts, admin panel, privacy, and sign-in regression tests.
7. Real-device testing before merge. Do not merge this PR until the owner finishes the remaining requested updates and explicitly approves.

## Important limitations

- Offline mode can show only content already cached/downloaded; never-downloaded remote content cannot be fetched without internet.
- Actual message delivery, calls, and remote uploads require connectivity. Offline sends/uploads are queued locally until a network is available.
