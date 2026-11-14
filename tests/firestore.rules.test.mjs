import fs from "node:fs";
import test, { before, after, beforeEach } from "node:test";
import assert from "node:assert/strict";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from "@firebase/rules-unit-testing";
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  writeBatch,
  arrayUnion,
  deleteField,
  query,
  where
} from "firebase/firestore";

const projectId = "demo-khahish-rules";
let env;

const activeStory = {
  ownerUid: "alice",
  ownerName: "Alice",
  text: "Hello",
  mediaUrl: "",
  mediaType: "text",
  createdAt: Date.now(),
  expiresAt: Date.now() + 60 * 60 * 1000,
  audience: "my_friends",
  allowedUids: ["bob"]
};

before(async () => {
  env = await initializeTestEnvironment({
    projectId,
    firestore: { rules: fs.readFileSync("firestore.rules", "utf8") }
  });
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

function db(uid) {
  return env.authenticatedContext(uid).firestore();
}

test("private user records are owner-only and login history is append-only", async () => {
  await assertSucceeds(setDoc(doc(db("alice"), "users/alice/fcmTokens/token1"), {
    token: "test-token",
    platform: "android",
    updatedAt: Date.now()
  }));
  await assertFails(getDoc(doc(db("bob"), "users/alice/fcmTokens/token1")));

  await assertSucceeds(setDoc(doc(db("alice"), "users/alice/loginHistory/entry1"), {
    time: Date.now(),
    deviceName: "Test device",
    platform: "Android"
  }));
  await assertFails(updateDoc(doc(db("alice"), "users/alice/loginHistory/entry1"), {
    deviceName: "tampered"
  }));
  await assertFails(updateDoc(doc(db("alice"), "users/alice/loginHistory/entry1"), {
    time: Date.now()
  }));
});

test("blocked-account records are private to the blocker", async () => {
  await seed("users/alice/blocked/bob", {
    uid: "bob",
    name: "Bob",
    createdAt: Date.now()
  });

  await assertSucceeds(getDoc(doc(db("alice"), "users/alice/blocked/bob")));
  await assertFails(getDoc(doc(db("bob"), "users/alice/blocked/bob")));
  await assertFails(getDoc(doc(db("carol"), "users/alice/blocked/bob")));
  await assertFails(getDocs(collection(db("bob"), "users/alice/blocked")));
});

test("public profiles never expose private contact fields", async () => {
  const safeProfile = {
    uid: "alice",
    name: "Alice",
    username: "alice_name",
    photoUrl: ""
  };
  await assertSucceeds(setDoc(doc(db("alice"), "publicProfiles/alice"), safeProfile));
  await assertSucceeds(getDoc(doc(db("bob"), "publicProfiles/alice")));

  await assertFails(setDoc(doc(db("alice"), "publicProfiles/unsafe"), {
    uid: "alice",
    name: "Alice",
    email: "alice@example.test"
  }));

  await seed("publicProfiles/legacy", {
    uid: "legacy",
    name: "Legacy User",
    email: "legacy@example.test",
    contact: "+10000000000"
  });
  await assertFails(getDoc(doc(db("bob"), "publicProfiles/legacy")));

  await assertSucceeds(updateDoc(doc(db("legacy"), "publicProfiles/legacy"), {
    email: deleteField(),
    contact: deleteField()
  }));
  await assertSucceeds(getDoc(doc(db("bob"), "publicProfiles/legacy")));
  await assertFails(updateDoc(doc(db("bob"), "publicProfiles/alice"), {
    name: "Changed by another user"
  }));
});

test("a username claim cannot be taken over by another account", async () => {
  await seed("usernames/alice_name", {
    uid: "alice",
    username: "alice_name",
    updatedAt: Date.now()
  });

  await assertFails(setDoc(doc(db("bob"), "usernames/alice_name"), {
    uid: "bob",
    username: "alice_name",
    updatedAt: Date.now()
  }));
  await assertSucceeds(setDoc(doc(db("alice"), "usernames/alice_name"), {
    uid: "alice",
    username: "alice_name",
    updatedAt: Date.now()
  }));
});

test("group creation requires at least three members", async () => {
  const group = {
    members: ["alice", "bob", "carol"],
    memberNames: { alice: "Alice", bob: "Bob", carol: "Carol" },
    isGroup: true,
    groupName: "Friends",
    groupAdmins: ["alice"],
    createdBy: "alice",
    createdAt: Date.now(),
    lastMessage: "Group created",
    lastMessageAt: Date.now(),
    updatedAt: Date.now(),
    disappearingDurationMs: 0,
    visibleToUids: ["alice", "bob", "carol"]
  };
  await assertSucceeds(setDoc(doc(db("alice"), "chats/group-three"), group));

  await assertFails(setDoc(doc(db("alice"), "chats/group-two"), {
    ...group,
    members: ["alice", "bob"],
    memberNames: { alice: "Alice", bob: "Bob" }
  }));
});

test("group membership changes keep the visible user index synchronized", async () => {
  await seed("chats/group-members", {
    members: ["alice", "bob", "carol"],
    memberNames: { alice: "Alice", bob: "Bob", carol: "Carol" },
    isGroup: true,
    groupName: "Friends",
    groupAdmins: ["alice"],
    createdBy: "alice",
    createdAt: Date.now(),
    lastMessage: "Group created",
    lastMessageAt: Date.now(),
    updatedAt: Date.now(),
    visibleToUids: ["alice", "bob", "carol"]
  });

  await assertSucceeds(updateDoc(doc(db("alice"), "chats/group-members"), {
    members: ["alice", "bob", "carol", "dave"],
    visibleToUids: ["alice", "bob", "carol", "dave"],
    memberNames: { alice: "Alice", bob: "Bob", carol: "Carol", dave: "Dave" },
    updatedAt: Date.now()
  }));

  // A regular member can leave only while the group remains at least three members.
  await assertSucceeds(updateDoc(doc(db("bob"), "chats/group-members"), {
    members: ["alice", "carol", "dave"],
    visibleToUids: ["alice", "carol", "dave"],
    updatedAt: Date.now()
  }));
});

test("secret-locked chat previews are excluded by the per-user server index", async () => {
  await seed("users/alice/secretChatLock/config", {
    passwordHash: "not-a-real-hash",
    lockedContactUids: ["bob"]
  });
  await seed("chats/alice_bob", {
    members: ["alice", "bob"],
    memberNames: { alice: "Alice", bob: "Bob" },
    lastMessage: "Private preview",
    lastMessageAt: Date.now(),
    updatedAt: Date.now(),
    visibleToUids: ["bob"]
  });

  const aliceQuery = query(
    collection(db("alice"), "chats"),
    where("visibleToUids", "array-contains", "alice")
  );
  const bobQuery = query(
    collection(db("bob"), "chats"),
    where("visibleToUids", "array-contains", "bob")
  );
  await assertSucceeds(getDocs(aliceQuery));
  await assertSucceeds(getDocs(bobQuery));
  const aliceResults = await getDocs(aliceQuery);
  const bobResults = await getDocs(bobQuery);
  assert.equal(aliceResults.size, 0);
  assert.equal(bobResults.size, 1);

  await assertFails(getDoc(doc(db("alice"), "chats/alice_bob")));
  await assertSucceeds(getDoc(doc(db("bob"), "chats/alice_bob")));

  // The Locked Chats hub uses direct document reads, gated by the server session.
  await seed("users/alice/secretChatLock/config/sessions/current", {
    expiresAt: new Date(Date.now() + 10 * 60 * 1000)
  });
  await assertSucceeds(getDoc(doc(db("alice"), "chats/alice_bob")));

  await assertFails(updateDoc(doc(db("alice"), "chats/alice_bob"), {
    visibleToUids: ["alice", "bob"]
  }));
});

test("private chat is created only in the same batch that accepts its request", async () => {
  await seed("messageRequests/alice_bob", {
    senderUid: "alice",
    receiverUid: "bob",
    senderName: "Alice",
    receiverName: "Bob",
    status: "pending",
    createdAt: Date.now()
  });

  const receiverDb = db("bob");
  const batch = writeBatch(receiverDb);
  const now = Date.now();
  batch.update(doc(receiverDb, "messageRequests/alice_bob"), {
    status: "accepted",
    acceptedBy: "bob",
    handledBy: "bob",
    acceptedAt: now
  });
  batch.set(doc(receiverDb, "chats/alice_bob"), {
    members: ["alice", "bob"],
    memberNames: { alice: "Alice", bob: "Bob" },
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: now,
    createdAt: now,
    createdBy: "bob",
    disappearingDurationMs: 0,
    // A newly created private chat is hidden from both users until the Worker
    // reads both lock configs and writes the final per-user visibility index.
    visibleToUids: []
  });
  await assertSucceeds(batch.commit());

  await assertFails(setDoc(doc(db("alice"), "chats/alice_carol"), {
    members: ["alice", "carol"],
    memberNames: { alice: "Alice", carol: "Carol" },
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: now,
    createdAt: now,
    createdBy: "alice",
    disappearingDurationMs: 0,
    visibleToUids: []
  }));
});

test("signed-in users may check an absent deterministic chat ID but cannot read another user's existing chat", async () => {
  const aliceDb = db("alice");
  await assertSucceeds(getDoc(doc(aliceDb, "chats/alice_bob")));

  await seed("chats/alice_carol", {
    members: ["alice", "carol"],
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now()
  });
  await assertSucceeds(getDoc(doc(aliceDb, "chats/alice_carol")));
  await assertFails(getDoc(doc(db("bob"), "chats/alice_carol")));
});

test("messages cannot be created while impersonating another chat member", async () => {
  await seed("chats/alice_bob", {
    members: ["alice", "bob"],
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now()
  });

  const validMessage = {
    senderId: "alice",
    text: "Hello",
    imageUrl: "",
    mediaType: "text",
    timestamp: Date.now(),
    deliveredTo: [],
    readBy: ["alice"]
  };
  await assertSucceeds(setDoc(doc(db("alice"), "chats/alice_bob/messages/message1"), validMessage));
  await assertFails(setDoc(doc(db("alice"), "chats/alice_bob/messages/message2"), {
    ...validMessage,
    senderId: "bob"
  }));
});

test("story audience requires an accepted friend and expiry is enforced", async () => {
  await seed("messageRequests/alice_bob", {
    senderUid: "alice",
    receiverUid: "bob",
    status: "accepted"
  });
  await seed("stories/active-friends", activeStory);
  // A malicious owner may forge a recipient UID in allowedUids, but that
  // recipient must still fail both direct reads and scoped list queries.
  await seed("stories/forged-recipient", {
    ...activeStory,
    allowedUids: ["charlie"]
  });
  await seed("stories/expired-friends", {
    ...activeStory,
    expiresAt: Date.now() - 60_000
  });
  await seed("stories/private", {
    ...activeStory,
    audience: "only_me",
    allowedUids: []
  });

  await assertSucceeds(getDoc(doc(db("bob"), "stories/active-friends")));
  await assertFails(getDoc(doc(db("charlie"), "stories/active-friends")));
  await assertFails(getDoc(doc(db("charlie"), "stories/forged-recipient")));
  await assertFails(getDoc(doc(db("bob"), "stories/expired-friends")));
  await assertFails(getDoc(doc(db("bob"), "stories/private")));
  await assertSucceeds(getDoc(doc(db("alice"), "stories/private")));

  const feedEntry = {
    ownerUid: "alice",
    viewerUid: "bob",
    storyId: "active-friends",
    createdAt: Date.now(),
    expiresAt: Date.now() + 60 * 60 * 1000
  };
  await assertSucceeds(setDoc(
    doc(db("alice"), "users/bob/storyFeed/alice/stories/active-friends"),
    feedEntry
  ));
  await assertFails(setDoc(
    doc(db("alice"), "users/charlie/storyFeed/alice/stories/forged-recipient"),
    {
      ...feedEntry,
      viewerUid: "charlie",
      storyId: "forged-recipient"
    }
  ));
  await assertSucceeds(getDocs(collection(
    db("bob"),
    "users/bob/storyFeed/alice/stories"
  )));
  await assertFails(getDocs(collection(
    db("charlie"),
    "users/charlie/storyFeed/alice/stories"
  )));
  await assertFails(getDocs(query(
    collection(db("bob"), "stories"),
    where("ownerUid", "==", "alice"),
    where("allowedUids", "array-contains", "bob"),
    where("expiresAt", ">", Date.now())
  )));
  await assertFails(getDocs(query(
    collection(db("bob"), "stories"),
    where("audience", "==", "everyone"),
    where("expiresAt", ">", Date.now())
  )));
  await assertSucceeds(getDocs(query(
    collection(db("alice"), "stories"),
    where("ownerUid", "==", "alice"),
    where("expiresAt", ">", Date.now())
  )));
});

test("live location writes are limited to the matching chat member UID", async () => {
  await seed("chats/alice_bob", {
    members: ["alice", "bob"],
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now()
  });
  const point = {
    uid: "alice",
    latitude: 33.7,
    longitude: 73.0,
    accuracy: 10,
    updatedAt: Date.now(),
    startedAt: Date.now(),
    expiresAt: Date.now() + 60_000,
    isActive: true
  };
  await assertSucceeds(setDoc(doc(db("alice"), "chats/alice_bob/liveLocations/alice"), point));
  await assertFails(setDoc(doc(db("bob"), "chats/alice_bob/liveLocations/alice"), {
    ...point,
    uid: "alice"
  }));
});

test("user reports use the app's targetUid schema and are reporter-authenticated", async () => {
  await assertSucceeds(setDoc(doc(db("alice"), "reports/report1"), {
    type: "user",
    targetUid: "bob",
    targetName: "Bob",
    reporterUid: "alice",
    reason: "Abusive messages",
    status: "pending",
    createdAt: Date.now()
  }));
  await assertFails(setDoc(doc(db("alice"), "reports/report2"), {
    type: "user",
    targetUid: "carol",
    targetName: "Carol",
    reporterUid: "bob",
    reason: "Impersonation",
    status: "pending",
    createdAt: Date.now()
  }));
  await assertFails(setDoc(doc(db("alice"), "reports/report3"), {
    type: "user",
    targetId: "bob",
    reporterUid: "alice",
    status: "pending",
    createdAt: Date.now()
  }));
});

test("deactivation requests are allowed but deletion requests are denied", async () => {
  await assertSucceeds(setDoc(doc(db("alice"), "accountRequests/alice"), {
    uid: "alice",
    type: "deactivate",
    status: "pending",
    createdAt: Date.now(),
    email: "alice@example.test",
    note: "Requested in test"
  }));
  await assertFails(setDoc(doc(db("bob"), "accountRequests/bob"), {
    uid: "bob",
    type: "delete",
    status: "pending",
    createdAt: Date.now()
  }));
});

test("call records are visible only to participants and support incoming-call queries", async () => {
  await seed("calls/call1", {
    callerUid: "alice",
    calleeUid: "bob",
    callerName: "Alice",
    calleeName: "Bob",
    participants: ["alice", "bob"],
    channelName: "khahish_test",
    callType: "voice",
    status: "ringing",
    createdAt: Date.now(),
    updatedAt: Date.now()
  });

  await assertSucceeds(getDoc(doc(db("bob"), "calls/call1")));
  await assertFails(getDoc(doc(db("carol"), "calls/call1")));
  await assertSucceeds(getDocs(query(
    collection(db("bob"), "calls"),
    where("calleeUid", "==", "bob")
  )));
  await assertSucceeds(getDocs(query(
    collection(db("alice"), "calls"),
    where("participants", "array-contains", "alice")
  )));
  const acceptedAt = Date.now();
  await assertSucceeds(updateDoc(doc(db("bob"), "calls/call1"), {
    status: "accepted",
    updatedAt: acceptedAt,
    acceptedAt
  }));
  await assertFails(updateDoc(doc(db("alice"), "calls/call1"), {
    status: "accepted",
    updatedAt: Date.now(),
    acceptedAt: Date.now()
  }));
  const endedAt = Date.now();
  await assertSucceeds(updateDoc(doc(db("bob"), "calls/call1"), {
    status: "ended",
    updatedAt: endedAt,
    endedAt
  }));
  await assertFails(updateDoc(doc(db("carol"), "calls/call1"), {
    status: "ended",
    updatedAt: Date.now()
  }));
});

test("caller timeout and incoming-call back navigation can close ringing calls", async () => {
  const base = {
    callerUid: "alice",
    calleeUid: "bob",
    callerName: "Alice",
    calleeName: "Bob",
    participants: ["alice", "bob"],
    channelName: "khahish_timeout",
    callType: "voice",
    status: "ringing",
    createdAt: Date.now(),
    updatedAt: Date.now()
  };
  await seed("calls/caller-timeout", base);
  await seed("calls/callee-back", { ...base, channelName: "khahish_back" });

  await assertSucceeds(updateDoc(doc(db("alice"), "calls/caller-timeout"), {
    status: "missed",
    updatedAt: Date.now()
  }));
  const callerEndedAt = Date.now();
  await assertSucceeds(updateDoc(doc(db("alice"), "calls/caller-timeout"), {
    status: "ended",
    updatedAt: callerEndedAt,
    endedAt: callerEndedAt
  }));
  const calleeEndedAt = Date.now();
  await assertSucceeds(updateDoc(doc(db("bob"), "calls/callee-back"), {
    status: "ended",
    updatedAt: calleeEndedAt,
    endedAt: calleeEndedAt
  }));
});


test("support tickets are private to their owner and only admins can moderate", async () => {
  const ticket = {
    userUid: "alice",
    userEmail: "alice@example.com",
    subject: "Cannot receive messages",
    message: "I cannot receive messages from my contacts after the latest update.",
    status: "pending",
    adminReply: "",
    createdAt: Date.now(),
    updatedAt: Date.now()
  };

  const aliceDb = db("alice");
  const ticketRef = doc(aliceDb, "supportTickets/ticket-alice");
  await assertSucceeds(setDoc(ticketRef, ticket));
  await assertSucceeds(getDoc(ticketRef));
  await assertSucceeds(getDocs(query(collection(aliceDb, "supportTickets"), where("userUid", "==", "alice"))));

  await assertFails(getDoc(doc(db("bob"), "supportTickets/ticket-alice")));
  await assertFails(getDocs(collection(db("bob"), "supportTickets")));
  await assertFails(updateDoc(ticketRef, {
    status: "resolved",
    adminReply: "This should be admin-only.",
    updatedAt: Date.now()
  }));

  const adminDb = env.authenticatedContext("support-admin", { admin: true }).firestore();
  const adminTicketRef = doc(adminDb, "supportTickets/ticket-alice");
  await assertSucceeds(getDoc(adminTicketRef));
  await assertSucceeds(getDocs(collection(adminDb, "supportTickets")));
  await assertSucceeds(updateDoc(adminTicketRef, {
    status: "in_progress",
    adminReply: "We are investigating this issue.",
    updatedAt: Date.now()
  }));

  await assertFails(setDoc(doc(db("bob"), "supportTickets/forged-ticket"), {
    ...ticket,
    userUid: "alice"
  }));
  await assertFails(setDoc(doc(aliceDb, "supportTickets/short-ticket"), {
    ...ticket,
    subject: "Hi",
    message: "Short"
  }));
});

test("reports can only be read by an admin", async () => {
  await seed("reports/report-private", {
    type: "user",
    targetUid: "bob",
    targetName: "Bob",
    reporterUid: "alice",
    reason: "Spam",
    status: "pending",
    createdAt: Date.now()
  });

  await assertFails(getDoc(doc(db("alice"), "reports/report-private")));
  await assertFails(getDocs(collection(db("alice"), "reports")));

  const adminDb = env.authenticatedContext("moderator", { admin: true }).firestore();
  await assertSucceeds(getDoc(doc(adminDb, "reports/report-private")));
  await assertSucceeds(getDocs(collection(adminDb, "reports")));
});

test("message receipts can only be advanced by the receiving member", async () => {
  await seed("chats/alice_bob", {
    members: ["alice", "bob"],
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now()
  });
  const base = {
    senderId: "alice",
    text: "Hello",
    imageUrl: "",
    mediaType: "text",
    timestamp: Date.now(),
    deliveredTo: [],
    readBy: ["alice"]
  };
  await seed("chats/alice_bob/messages/receipt-valid", base);
  await seed("chats/alice_bob/messages/receipt-forged", base);

  await assertSucceeds(updateDoc(doc(db("bob"), "chats/alice_bob/messages/receipt-valid"), {
    deliveredTo: ["bob"],
    readBy: ["alice", "bob"]
  }));
  await assertFails(updateDoc(doc(db("bob"), "chats/alice_bob/messages/receipt-forged"), {
    deliveredTo: ["carol"],
    readBy: ["alice", "carol"]
  }));
  await assertFails(getDoc(doc(db("carol"), "chats/alice_bob/messages/receipt-valid")));
});

test("backup imports preserve historical sender labels but are explicitly unverified", async () => {
  await seed("chats/alice_bob", {
    members: ["alice", "bob"],
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now()
  });

  const restoredAt = Date.now();
  const imported = {
    senderId: "bob",
    text: "Historical message from backup",
    imageUrl: "",
    mediaType: "text",
    timestamp: restoredAt - 60_000,
    deliveredTo: ["alice"],
    readBy: ["bob", "alice"],
    restoredFromBackup: true,
    restoredBy: "alice",
    restoredAt,
    backupMemberUids: ["alice", "bob"]
  };
  const importedRef = doc(db("alice"), "chats/alice_bob/messages/backup_alice_old1");
  await assertSucceeds(setDoc(importedRef, imported));
  await assertSucceeds(updateDoc(importedRef, {
    text: "Re-imported historical message",
    restoredAt: Date.now()
  }));
  await assertFails(updateDoc(doc(db("bob"), "chats/alice_bob/messages/backup_alice_old1"), {
    text: "Another member must not edit Alice's imported record",
    editedAt: Date.now()
  }));
  await assertFails(setDoc(doc(db("alice"), "chats/alice_bob/messages/backup_alice_forged"), {
    ...imported,
    restoredBy: "bob"
  }));
  await assertFails(setDoc(doc(db("alice"), "chats/alice_bob/messages/backup_alice_future"), {
    ...imported,
    timestamp: restoredAt + 60_000
  }));

  await seed("chats/alice_carol", {
    members: ["alice", "carol"],
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now()
  });
  await assertSucceeds(setDoc(doc(db("alice"), "chats/alice_carol/messages/backup_alice_former_member"), {
    ...imported,
    senderId: "bob",
    backupMemberUids: ["alice", "bob", "carol"]
  }));
});

test("only group admins can add members, while ordinary members can leave", async () => {
  await seed("chats/group-admin-test", {
    members: ["alice", "bob", "carol"],
    memberNames: { alice: "Alice", bob: "Bob", carol: "Carol" },
    isGroup: true,
    groupName: "Friends",
    groupAdmins: ["alice"],
    createdBy: "alice",
    createdAt: Date.now(),
    lastMessage: "",
    lastMessageAt: 0,
    updatedAt: Date.now(),
    visibleToUids: ["alice", "bob", "carol"]
  });

  await assertSucceeds(updateDoc(doc(db("alice"), "chats/group-admin-test"), {
    members: arrayUnion("dave"),
    visibleToUids: arrayUnion("dave"),
    "memberNames.dave": "Dave",
    updatedAt: Date.now()
  }));

  await assertFails(updateDoc(doc(db("bob"), "chats/group-admin-test"), {
    members: arrayUnion("erin"),
    visibleToUids: arrayUnion("erin"),
    "memberNames.erin": "Erin",
    updatedAt: Date.now()
  }));

  await assertSucceeds(updateDoc(doc(db("bob"), "chats/group-admin-test"), {
    members: ["alice", "carol", "dave"],
    visibleToUids: ["alice", "carol", "dave"],
    updatedAt: Date.now()
  }));

  // The group must never fall below the three-member minimum.
  await assertFails(updateDoc(doc(db("alice"), "chats/group-admin-test"), {
    members: ["alice", "carol"],
    updatedAt: Date.now()
  }));

  await assertFails(updateDoc(doc(db("carol"), "chats/group-admin-test"), {
    members: ["alice", "dave"],
    updatedAt: Date.now()
  }));
});

test("active account restrictions block ordinary accounts but expire automatically", async () => {
  await seed("publicProfiles/alice", {
    uid: "alice",
    name: "Alice",
    username: "alice_name"
  });
  await seed("accountRestrictions/bob", {
    uid: "bob",
    permanent: true,
    expiresAt: 0,
    reason: "Test ban"
  });

  await assertFails(getDoc(doc(db("bob"), "publicProfiles/alice")));
  await assertFails(setDoc(doc(db("bob"), "users/bob/loginHistory/blocked"), {
    time: Date.now(),
    deviceName: "Blocked device",
    platform: "Android"
  }));

  const ownerDb = env.authenticatedContext("owner", { admin: true }).firestore();
  await assertSucceeds(getDoc(doc(ownerDb, "accountRestrictions/bob")));
  await assertSucceeds(setDoc(doc(ownerDb, "accountRestrictions/bob"), {
    uid: "bob",
    permanent: false,
    expiresAt: Date.now() - 1000,
    reason: "Expired"
  }));

  await assertSucceeds(getDoc(doc(db("bob"), "publicProfiles/alice")));
});

test("only an admin can list moderation profiles and queue broadcasts", async () => {
  await seed("publicProfiles/alice", {
    uid: "alice",
    name: "Alice",
    username: "alice_name",
    nameLowercase: "alice"
  });

  await assertFails(getDocs(collection(db("bob"), "publicProfiles")));
  await assertFails(setDoc(doc(db("bob"), "adminBroadcasts/regular-user"), {
    title: "Hello",
    body: "Not allowed",
    targetType: "all",
    createdBy: "bob",
    createdAt: Date.now()
  }));

  const ownerDb = env.authenticatedContext("owner", { admin: true }).firestore();
  await assertSucceeds(getDocs(collection(ownerDb, "publicProfiles")));
  await assertSucceeds(setDoc(doc(ownerDb, "adminBroadcasts/owner-message"), {
    title: "Khahish update",
    body: "A message for everyone.",
    kind: "announcement",
    targetType: "all",
    createdBy: "owner",
    createdAt: Date.now()
  }));
  await assertFails(updateDoc(doc(ownerDb, "adminBroadcasts/owner-message"), {
    dispatchStatus: "sent"
  }));
});

test("admin audit logs are append-only and private to admins", async () => {
  const ownerDb = env.authenticatedContext("owner", { admin: true }).firestore();
  await assertSucceeds(setDoc(doc(ownerDb, "adminAuditLogs/log1"), {
    actorUid: "owner",
    action: "user_banned",
    targetUid: "bob",
    createdAt: Date.now()
  }));
  await assertSucceeds(getDocs(collection(ownerDb, "adminAuditLogs")));
  await assertFails(getDocs(collection(db("bob"), "adminAuditLogs")));
  await assertFails(updateDoc(doc(ownerDb, "adminAuditLogs/log1"), {
    action: "changed"
  }));
});
