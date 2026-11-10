# Khahish E2EE and Linked-Device Architecture

Status: design and compatibility audit; not yet enabled in production.
Branch: feature/agora-calling
Constraints: Firebase Spark Free; Android-first; preserve existing chat history and backup/restore; no account deletion, guest mode, multiple-account switching, Communities, or two-step verification.

## 1. Current implementation findings

- Chat messages are currently stored as plaintext Firestore fields: senderId, text, imageUrl, mediaType, timestamp, receipts, replyToText, reactions, and other metadata.
- Images, video, and audio are uploaded to Cloudinary before their returned HTTPS URL is written into the message. Encrypting only the Firestore text would NOT protect media.
- Chat previews (lastMessage) are also stored in the chat document as readable plaintext.
- Search currently depends on readable message text and must become local, on-device search over decrypted history.
- Existing backup/restore preserves historical message fields and marks imported history as unverified. E2EE migration must not silently claim old plaintext backups are encrypted.
- Existing trustedDevices documents contain deviceName, platform, lastActiveAt, and trusted. They are revocation/recognition records, not cryptographic linked-device pairing or key distribution.
- Current Android project targets Java 17. Signal's current libsignal README states its officially supported Android JDK is 21 and says use outside Signal is unsupported; its APIs and bridge layers may change without notice. Therefore no libsignal dependency is being added until an isolated proof-of-compatibility build and API review succeeds.

## 2. Security goals

1. Message and media content must be encrypted on sender devices before upload/write.
2. Only authorized recipient devices can decrypt; Firebase, Cloudinary, push sender, and other server-side services must not receive message plaintext or private keys.
3. Use a reviewed protocol implementation. Do not invent a ratchet, key exchange, nonce scheme, or group crypto.
4. Bind ciphertext to chat ID, message ID, sender device ID, recipient/device context, and protocol version as authenticated associated data where the selected protocol supports it.
5. Handle identity-key changes and new devices explicitly; never silently trust a changed identity.
6. Revoke a linked device and prevent future key delivery to it. Explain that revocation cannot erase plaintext or keys already copied by that device.
7. Preserve old messages as explicitly marked legacy plaintext until users choose a supported migration/backup path.
8. Keep notifications generic. Never include message text, contact details, or media content in push payloads.
9. Keep plaintext only in memory/UI as needed; never log keys, plaintext, QR pairing secrets, or decrypted payloads.

## 3. Protocol selection gates

### One-to-one chats
Evaluate the current Signal Protocol implementation (libsignal) for API suitability, licensing, build compatibility, Android minSdk/ABI coverage, and whether the project can responsibly maintain an unsupported external integration. Do not use the archived libsignal-protocol-java as a new dependency. If libsignal cannot be responsibly integrated, stop and select another independently audited protocol/library rather than building custom cryptography.

Required protocol properties:
- asynchronous prekey-based session establishment;
- Double Ratchet forward secrecy and post-compromise security;
- authenticated identity keys and signed prekeys;
- per-device sessions and safe session-state persistence;
- replay/duplicate handling and skipped-message-key limits;
- explicit identity verification/change warnings.

### Group chats
Do not reuse a one-to-one primitive by encrypting one message with a shared static group key. Select an audited group-messaging protocol/library with documented membership-change and key-rotation semantics. Group creation, member addition/removal, admin changes, and device addition/removal must trigger protocol-defined epoch/key updates. If a safe group protocol is not ready, keep group chats clearly marked as not end-to-end encrypted rather than implying full E2EE.

## 4. Proposed Firestore data model (public keys and ciphertext only)

- users/{uid}/devices/{deviceId}: deviceId, deviceLabel, platform, createdAt, lastActiveAt, status, protocolVersion, identityPublicKey, signedPreKeyPublic, signedPreKeySignature, signedPreKeyId, publishedAt. No private key material.
- users/{uid}/devices/{deviceId}/oneTimePreKeys/{preKeyId}: public prekey and consumed/claim metadata, with atomic single-use claim rules.
- chats/{chatId}/messages/{messageId}: senderId, senderDeviceId, protocol, protocolVersion, ciphertext, header/envelope, timestamp, delivery/read metadata, expiry metadata. Do not store text, reply text, media URL, or decrypted preview in this record.
- chats/{chatId}: membership and non-sensitive metadata only. lastMessage must become a generic marker such as Encrypted message; no plaintext preview.
- users/{uid}/devices/{deviceId}/sessions/{peerDeviceId}: encrypted/serialized protocol session state only if the selected library's storage model permits it; otherwise store session state locally in encrypted app-private storage. Never put private identity keys in Firestore.
- users/{uid}/devicePairRequests/{requestId}: short-lived pairing challenge, requesting device public key, expiry, and approval status; no secrets/private keys.

All schemas are provisional and must be aligned to the chosen protocol implementation before rules are written.

## 5. Media and attachment flow

1. Read selected media locally.
2. Encrypt bytes locally using the protocol's recommended attachment-encryption design with a fresh random per-attachment key and authenticated encryption.
3. Upload ciphertext only to Cloudinary/object storage.
4. Put the encrypted attachment pointer, key material, nonce/metadata, integrity data, and MIME/filename metadata inside the E2EE message envelope. Ensure the envelope itself is encrypted for each recipient device.
5. Download ciphertext and decrypt locally only after successful authorization.
6. Do not expose public plaintext media URLs. Existing Cloudinary uploads are legacy plaintext and must be labeled as such until re-shared encrypted.

## 6. Linked-device pairing and revocation

1. A new Android installation creates a new device identity and key bundle locally.
2. The new device displays a short-lived QR pairing request containing only a random challenge, device public key/fingerprint, protocol version, and request identifier.
3. An already trusted device scans and verifies the new device identity/fingerprint; user confirms the exact device label.
4. Pairing approval is authenticated and bound to the challenge; derive a one-time encrypted transfer channel. Never put a private key or reusable account secret in the QR code.
5. Transfer only the protocol-approved account/device state, then register the new device's public prekeys.
6. New device receives its own per-device sessions/keys. Do not copy a single private key to every device unless the reviewed protocol explicitly requires and securely supports it.
7. Revoke by marking device revoked, removing its prekeys from future distribution, rotating/re-establishing affected sessions/group epochs, and signing out its Firebase session where possible.
8. State clearly that revocation prevents future access/delivery but cannot remotely erase previously copied plaintext or keys.

## 7. Migration and compatibility

- Add explicit encryptionState / protocolVersion markers to messages and chat metadata.
- Legacy messages without an encryption marker remain readable as legacy plaintext; never try to decrypt them.
- New E2EE messages must fail closed if keys/session state are missing; do not silently fall back to plaintext.
- Existing search becomes a local decrypted-history index. Do not upload search terms or decrypted text.
- Backup export must be opt-in and clearly distinguish encrypted protocol backup from plaintext legacy export. Any encrypted backup needs a user-controlled recovery secret or an audited recovery mechanism; never derive keys directly from a short PIN without a proper KDF.
- Restore must validate format/version and label unverified imported history. It must not create trusted devices automatically.
- Existing receipts, reactions, disappearing messages, edits, replies, and forwarding must be reviewed for metadata leakage and protocol compatibility.

## 8. Rollout and test gates

Phase A — protocol/build proof:
- Verify current Android build toolchain against candidate library; current app uses Java 17, while libsignal documents JDK 21 as its supported Android JDK.
- Build a separate non-production proof module and run upstream/library test vectors.
- Review license, support status, API stability, native ABIs, minSdk 24, APK size, and CI reproducibility.

Phase B — one-to-one:
- Device key creation/storage and public prekey publishing.
- Session establishment and encrypted text messages.
- Multi-device recipient fanout.
- Tamper, replay, duplicate, out-of-order, offline, reinstall, and identity-change tests.
- Ensure server and push payloads contain no plaintext.

Phase C — encrypted media and compatibility:
- Encrypted image/audio/video/document upload/download.
- Local search and encrypted/legacy message display.
- Backup/restore migration tests.

Phase D — linked devices:
- QR pairing, approval, encrypted transfer, key registration, revoke, offline/reconnect, and device-loss tests.

Phase E — groups:
- Only after selecting and integrating an audited group protocol; test add/remove member, device changes, epoch rotation, offline members, and forward secrecy.

No phase is complete until CI passes and two or more real Android devices verify send/receive, media, offline delivery, reinstall, and revocation. Do not publish Firestore rules or deploy backend changes before the schema and security tests are reviewed.

## 9. Explicit non-goals

This work does not add Communities, Two-Step Verification, account deletion, Guest Mode, multiple-account switching, automatic media saving, or Screen Reader/TalkBack settings. Firebase Spark Free remains the hosting constraint.