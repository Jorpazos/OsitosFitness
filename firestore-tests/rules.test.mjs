import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, orderBy, limit, getDocs, runTransaction, increment, writeBatch, deleteField } from "firebase/firestore";
import fs from "fs";

const env = await initializeTestEnvironment({
  projectId: "ositos-test",
  firestore: { rules: fs.readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8085 },
});
const db = (u) => env.authenticatedContext(u, { email: `${u}@gmail.com` }).firestore();
const A = db("alice"), B = db("bob"), C = db("carol");
const anon = env.unauthenticatedContext().firestore();
let pass = 0, fail = 0;
async function t(name, p) { try { await p; pass++; console.log("ok  ", name); } catch (e) { fail++; console.log("FAIL", name, e.message); } }

async function join(f, uid) {
  return runTransaction(f, async (tx) => {
    const ref = doc(f, "duos/main");
    const s = await tx.get(ref);
    if (!s.exists()) { tx.set(ref, { members: [uid], createdAt: 1 }); return "CREATED"; }
    const m = s.get("members");
    if (m.includes(uid)) return "ALREADY";
    if (m.length >= 2) return "FULL";
    tx.update(ref, { members: [...m, uid] }); return "JOINED";
  });
}

await t("anon cannot read duo", assertFails(getDoc(doc(anon, "duos/main"))));
await t("alice creates duo", assertSucceeds(join(A, "alice")));
await t("carol cannot create with 2 members", assertFails(setDoc(doc(C, "duos/main"), { members: ["carol", "x"] })));
await t("bob joins", assertSucceeds(join(B, "bob")));
await t("alice rejoin is noop", assertSucceeds(join(A, "alice")));
await t("carol cannot read full duo", assertFails(getDoc(doc(C, "duos/main"))));
await t("carol cannot add herself", assertFails(updateDoc(doc(C, "duos/main"), { members: ["alice", "bob", "carol"] })));

await t("alice writes own profile", assertSucceeds(setDoc(doc(A, "duos/main/profiles/alice"), { uid: "alice", nickname: "Osita" }, { merge: true })));
await t("bob writes own profile", assertSucceeds(setDoc(doc(B, "duos/main/profiles/bob"), { uid: "bob", nickname: "Oso" }, { merge: true })));
await t("alice cannot write bob profile", assertFails(setDoc(doc(A, "duos/main/profiles/bob"), { nickname: "x" }, { merge: true })));
await t("bob reads profiles list", assertSucceeds(getDocs(collection(B, "duos/main/profiles"))));
await t("carol cannot read profiles", assertFails(getDocs(collection(C, "duos/main/profiles"))));

const batch = writeBatch(A);
batch.set(doc(A, "duos/main/days/alice_2026-10-07"), { uid: "alice", dayKey: "2026-10-07", goalKcal: 1800, meals: increment(1), kcalIn: increment(400) }, { merge: true });
batch.set(doc(A, "duos/main/logs/l1"), { uid: "alice", type: "MEAL", ts: 1, dayKey: "2026-10-07", title: "Milanesa", detail: "", emoji: "🥩", kcal: 400, weightKg: null, reactions: {} });
await t("alice logs meal (batch day+log)", assertSucceeds(batch.commit()));
await t("alice decrement merge", assertSucceeds(setDoc(doc(A, "duos/main/days/alice_2026-10-07"), { meals: increment(-1) }, { merge: true })));
await t("alice cannot write bob day", assertFails(setDoc(doc(A, "duos/main/days/bob_2026-10-07"), { uid: "alice", meals: 1 })));
await t("alice cannot spoof uid in day", assertFails(setDoc(doc(A, "duos/main/days/alice_2026-10-08"), { uid: "bob", meals: 1 })));
await t("alice weight", assertSucceeds(setDoc(doc(A, "duos/main/weights/alice_2026-10-07"), { uid: "alice", dayKey: "2026-10-07", kg: 70 })));
await t("bob reads days", assertSucceeds(getDocs(collection(B, "duos/main/days"))));
await t("bob reacts", assertSucceeds(updateDoc(doc(B, "duos/main/logs/l1"), { "reactions.bob": "🔥" })));
await t("bob removes reaction", assertSucceeds(updateDoc(doc(B, "duos/main/logs/l1"), { "reactions.bob": deleteField() })));
await t("bob cannot react as alice", assertFails(updateDoc(doc(B, "duos/main/logs/l1"), { "reactions.alice": "🔥" })));
await t("bob cannot edit title", assertFails(updateDoc(doc(B, "duos/main/logs/l1"), { title: "Ensalada" })));
await t("bob cannot delete alice log", assertFails(deleteDoc(doc(B, "duos/main/logs/l1"))));
await t("bob cannot create log as alice", assertFails(setDoc(doc(B, "duos/main/logs/l2"), { uid: "alice", ts: 2 })));
await t("logs ordered query by member", assertSucceeds(getDocs(query(collection(B, "duos/main/logs"), orderBy("ts", "desc"), limit(200)))));
await t("logs query by carol fails", assertFails(getDocs(query(collection(C, "duos/main/logs"), orderBy("ts", "desc"), limit(200)))));
await t("alice deletes own log", assertSucceeds(deleteDoc(doc(A, "duos/main/logs/l1"))));

await t("alice pokes bob", assertSucceeds(setDoc(doc(A, "duos/main/pokes/p1"), { from: "alice", to: "bob", fromName: "Osita", message: "dale", ts: 1, delivered: false })));
await t("alice cannot poke as bob", assertFails(setDoc(doc(A, "duos/main/pokes/p2"), { from: "bob", to: "alice", message: "x", ts: 1, delivered: false })));
await t("bob pokes query", assertSucceeds(getDocs(query(collection(B, "duos/main/pokes"), where("to", "==", "bob"), where("delivered", "==", false)))));
await t("alice cannot mark delivered", assertFails(updateDoc(doc(A, "duos/main/pokes/p1"), { delivered: true })));
await t("bob marks delivered", assertSucceeds(updateDoc(doc(B, "duos/main/pokes/p1"), { delivered: true })));
await t("pokesSent increment own profile", assertSucceeds(setDoc(doc(A, "duos/main/profiles/alice"), { pokesSent: increment(1) }, { merge: true })));

await t("member updates duo lists", assertSucceeds(updateDoc(doc(A, "duos/main"), { forfeits: ["Masajes"], "duelsWon.alice": increment(1) })));
await t("member cannot kick partner", assertFails(updateDoc(doc(A, "duos/main"), { members: ["alice"] })));
await t("member cannot add 3rd", assertFails(updateDoc(doc(A, "duos/main"), { members: ["alice", "bob", "carol"] })));
await t("weeks write", assertSucceeds(setDoc(doc(B, "duos/main/weeks/2026-W41"), { forfeit: "Masajes", forfeitBy: "bob" }, { merge: true })));
await t("carol cannot read weeks", assertFails(getDoc(doc(C, "duos/main/weeks/2026-W41"))));
await t("aiUsage closed", assertFails(getDoc(doc(A, "duos/main/aiUsage/alice_2026-10-07"))));
await t("other paths closed", assertFails(setDoc(doc(A, "otra/cosa"), { a: 1 })));
await t("carol join attempt returns FULL (read denied)", assertFails(join(C, "carol")));

await env.cleanup();
console.log(`\n${pass} ok, ${fail} fail`);
process.exit(fail ? 1 : 0);
