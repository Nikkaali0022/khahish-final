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
  audience: "everyone"
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
    secretLockedFor_alice: false,
    secretLockedFor_bob: false,
    secretLockedFor_carol: false
  };
  await assertSucceeds(setDoc(doc(db("alice"), "chats/group-three"), group));

  await assertFails(setDoc(doc(db("alice"), "chats/group-two"), {
    ...group,
    members: ["alice", "bob"],
    memberNames: { alice: "Alice", bob: "Bob" }
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
    secretLockedFor_alice: true,
    secretLockedFor_bob: false
  });

  const aliceQuery = query(
    collection(db("alice"), "chats"),
    where("members", "array-contains", "alice"),
    where("secretLockedFor_alice", "==", false)
  );
  const bobQuery = query(
    collection(db("bob"), "chats"),
    where("members", "array-contains", "bob"),
    where("secretLockedFor_bob", "==", false)
  );
  await assertSucceeds(getDocs(aliceQuery));
  await assertSucceeds(getDocs(bobQuery));
  const aliceResults = await getDocs(aliceQuery);
  const bobResults = await getDocs(bobQuery);
  assert.equal(aliceResults.size, 0);
  assert.equal(bobResults.size, 1);

  await assertFails(getDoc(doc(db("alice"), "chats/alice_bob")));
  await assertSucceeds(getDoc(doc(db("bob"), "chats/alice_bob")));
  await assertFails(updateDoc(doc(db("alice"), "chats/alice_bob"), {
    secretLockedFor_alice: false
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
    secretLockedFor_alice: true,
    secretLockedFor_bob: true
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
    secretLockedFor_alice: true,
    secretLockedFor_carol: true
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

test("story audience and expiry are enforced", async () => {
  await seed("stories/active-public", activeStory);
  await seed("stories/expired-public", {
    ...activeStory,
    expiresAt: Date.now() - 60_000
  });
  await seed("stories/private", {
    ...activeStory,
    audience: "only_me"
  });

  await assertSucceeds(getDoc(doc(db("bob"), "stories/active-public")));
  await assertFails(getDoc(doc(db("bob"), "stories/expired-public")));
  await assertFails(getDoc(doc(db("bob"), "stories/private")));
  await assertSucceeds(getDoc(doc(db("alice"), "stories/private")));
  await assertSucceeds(getDocs(query(
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
    updatedAt: Date.now()
  });

  await assertSucceeds(updateDoc(doc(db("alice"), "chats/group-admin-test"), {
    members: arrayUnion("dave"),
    "memberNames.dave": "Dave",
    updatedAt: Date.now()
  }));

  await assertFails(updateDoc(doc(db("bob"), "chats/group-admin-test"), {
    members: arrayUnion("erin"),
    "memberNames.erin": "Erin",
    updatedAt: Date.now()
  }));

  await assertSucceeds(updateDoc(doc(db("bob"), "chats/group-admin-test"), {
    members: ["alice", "carol", "dave"],
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
