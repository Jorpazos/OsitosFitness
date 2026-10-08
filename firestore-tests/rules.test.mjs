import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, orderBy, limit, getDocs,
  runTransaction, increment, writeBatch, deleteField,
} from "firebase/firestore";
import fs from "fs";

const env = await initializeTestEnvironment({
  projectId: "ositos-test",
  firestore: { rules: fs.readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8085 },
});
const db = (u) => env.authenticatedContext(u, { email: `${u}@gmail.com` }).firestore();
const A = db("alice"), B = db("bob"), C = db("carol"), D = db("dave");
const anon = env.unauthenticatedContext().firestore();
let pass = 0, fail = 0;
async function t(name, p) {
  try { await p; pass++; console.log("ok  ", name); } catch (e) { fail++; console.log("FAIL", name, e.message); }
}

// Lo mismo que hace la app: crea users/{uid} + pins/{pin} en una transacción.
async function register(f, uid, pin, name) {
  return runTransaction(f, async (tx) => {
    const p = await tx.get(doc(f, `pins/${pin}`));
    if (p.exists()) throw new Error("PIN ocupado");
    tx.set(doc(f, `users/${uid}`), { uid, pin, name, duoId: null, createdAt: 1 });
    tx.set(doc(f, `pins/${pin}`), { uid, name, duoId: null });
  });
}

// Lo mismo que hace la app al poner el PIN del otro.
async function pair(f, me, myPin, partnerPin, duoId) {
  return runTransaction(f, async (tx) => {
    const pinSnap = await tx.get(doc(f, `pins/${partnerPin}`));
    if (!pinSnap.exists()) throw new Error("PIN inexistente");
    const other = pinSnap.get("uid");
    if (pinSnap.get("duoId")) throw new Error("ya emparejado");
    tx.set(doc(f, `duos/${duoId}`), { members: [me, other], createdAt: 1, pairedBy: me });
    tx.update(doc(f, `users/${me}`), { duoId });
    tx.update(doc(f, `users/${other}`), { duoId });
    tx.update(doc(f, `pins/${myPin}`), { duoId });
    tx.update(doc(f, `pins/${partnerPin}`), { duoId });
  });
}

// ---------- Registro y PINs ----------
await t("anon no lee pins", assertFails(getDoc(doc(anon, "pins/AAAAAA"))));
await t("alice se registra", assertSucceeds(register(A, "alice", "ALICE1", "Alice")));
await t("bob se registra", assertSucceeds(register(B, "bob", "BOB222", "Bob")));
await t("carol se registra", assertSucceeds(register(C, "carol", "CAROL3", "Carol")));
await t("dave se registra", assertSucceeds(register(D, "dave", "DAVE44", "Dave")));
await t("no se puede robar un PIN ajeno", assertFails(setDoc(doc(C, "pins/ALICE1"), { uid: "carol", duoId: null })));
await t("PIN con formato inválido", assertFails(setDoc(doc(C, "pins/abc"), { uid: "carol", duoId: null })));
await t("no se puede crear el user de otro", assertFails(setDoc(doc(C, "users/zed"), { uid: "zed", pin: "ZZZZZZ", duoId: null })));
await t("buscar un PIN", assertSucceeds(getDoc(doc(B, "pins/ALICE1"))));
await t("no se pueden listar PINs", assertFails(getDocs(collection(B, "pins"))));
await t("no se lee el user de un desconocido", assertFails(getDoc(doc(B, "users/alice"))));
await t("no se cambia el PIN propio", assertFails(updateDoc(doc(A, "users/alice"), { pin: "OTRO11" })));

// ---------- Emparejar ----------
await t("bob se empareja con el PIN de alice", assertSucceeds(pair(B, "bob", "BOB222", "ALICE1", "duoAB")));
await t("alice lee su user con duoId", assertSucceeds(getDoc(doc(A, "users/alice"))));
await t("alice lee el user de bob (mismo dúo)", assertSucceeds(getDoc(doc(A, "users/bob"))));
await t("carol no lee users de otro dúo", assertFails(getDoc(doc(C, "users/alice"))));
await t("carol no puede emparejarse con alice (ya emparejada)", pair(C, "carol", "CAROL3", "ALICE1", "duoCA").then(
  () => { throw new Error("debió fallar"); },
  () => undefined,
));
await t("carol no puede meter a alice a la fuerza", assertFails((async () => {
  const b = writeBatch(C);
  b.set(doc(C, "duos/duoX"), { members: ["carol", "alice"] });
  b.update(doc(C, "users/carol"), { duoId: "duoX" });
  b.update(doc(C, "users/alice"), { duoId: "duoX" });
  await b.commit();
})()));
await t("no se crea un dúo de 3", assertFails((async () => {
  const b = writeBatch(C);
  b.set(doc(C, "duos/duoY"), { members: ["carol", "dave", "x"] });
  b.update(doc(C, "users/carol"), { duoId: "duoY" });
  b.update(doc(C, "users/dave"), { duoId: "duoY" });
  await b.commit();
})()));
await t("no se crea un dúo sin actualizar los users", assertFails(setDoc(doc(C, "duos/duoZ"), { members: ["carol", "dave"] })));
await t("dave se empareja con carol (segundo dúo)", assertSucceeds(pair(D, "dave", "DAVE44", "CAROL3", "duoCD")));
await t("alice no puede cambiarse de dúo", assertFails(updateDoc(doc(A, "users/alice"), { duoId: "duoCD" })));

// ---------- Datos dentro del dúo ----------
await t("alice lee su dúo", assertSucceeds(getDoc(doc(A, "duos/duoAB"))));
await t("carol no lee el dúo de alice", assertFails(getDoc(doc(C, "duos/duoAB"))));
await t("alice escribe su perfil", assertSucceeds(setDoc(doc(A, "duos/duoAB/profiles/alice"), { uid: "alice", nickname: "Osita" }, { merge: true })));
await t("bob escribe su perfil", assertSucceeds(setDoc(doc(B, "duos/duoAB/profiles/bob"), { uid: "bob", nickname: "Oso" }, { merge: true })));
await t("alice no escribe el perfil de bob", assertFails(setDoc(doc(A, "duos/duoAB/profiles/bob"), { nickname: "x" }, { merge: true })));
await t("bob lee perfiles", assertSucceeds(getDocs(collection(B, "duos/duoAB/profiles"))));
await t("carol no lee perfiles de otro dúo", assertFails(getDocs(collection(C, "duos/duoAB/profiles"))));
await t("carol no escribe en otro dúo", assertFails(setDoc(doc(C, "duos/duoAB/profiles/carol"), { uid: "carol" })));

const batch = writeBatch(A);
batch.set(doc(A, "duos/duoAB/days/alice_2026-10-07"), { uid: "alice", dayKey: "2026-10-07", goalKcal: 1800, meals: increment(1), kcalIn: increment(400) }, { merge: true });
batch.set(doc(A, "duos/duoAB/logs/l1"), { uid: "alice", type: "MEAL", ts: 1, dayKey: "2026-10-07", title: "Milanesa", detail: "", emoji: "🥩", kcal: 400, weightKg: null, reactions: {} });
await t("alice registra comida", assertSucceeds(batch.commit()));
await t("alice no escribe el día de bob", assertFails(setDoc(doc(A, "duos/duoAB/days/bob_2026-10-07"), { uid: "alice", meals: 1 })));
await t("bob reacciona", assertSucceeds(updateDoc(doc(B, "duos/duoAB/logs/l1"), { "reactions.bob": "🔥" })));
await t("bob saca su reacción", assertSucceeds(updateDoc(doc(B, "duos/duoAB/logs/l1"), { "reactions.bob": deleteField() })));
await t("bob no reacciona como alice", assertFails(updateDoc(doc(B, "duos/duoAB/logs/l1"), { "reactions.alice": "🔥" })));
await t("bob no edita el título", assertFails(updateDoc(doc(B, "duos/duoAB/logs/l1"), { title: "Ensalada" })));
await t("bob no borra el log de alice", assertFails(deleteDoc(doc(B, "duos/duoAB/logs/l1"))));
await t("logs ordenados por miembro", assertSucceeds(getDocs(query(collection(B, "duos/duoAB/logs"), orderBy("ts", "desc"), limit(200)))));
await t("logs de otro dúo no", assertFails(getDocs(query(collection(C, "duos/duoAB/logs"), orderBy("ts", "desc"), limit(200)))));

await t("alice pincha a bob", assertSucceeds(setDoc(doc(A, "duos/duoAB/pokes/p1"), { from: "alice", to: "bob", message: "dale", ts: 1, delivered: false })));
await t("pinchazo con remitente falso", assertFails(setDoc(doc(A, "duos/duoAB/pokes/p2"), { from: "bob", to: "alice", message: "x", ts: 1, delivered: false })));
await t("bob consulta sus pinchazos", assertSucceeds(getDocs(query(collection(B, "duos/duoAB/pokes"), where("to", "==", "bob"), where("delivered", "==", false)))));
await t("alice no marca entregado", assertFails(updateDoc(doc(A, "duos/duoAB/pokes/p1"), { delivered: true })));
await t("bob marca entregado", assertSucceeds(updateDoc(doc(B, "duos/duoAB/pokes/p1"), { delivered: true })));

await t("miembro edita listas del dúo", assertSucceeds(updateDoc(doc(A, "duos/duoAB"), { forfeits: ["Masajes"], "duelsWon.alice": increment(1) })));
await t("miembro no echa al otro", assertFails(updateDoc(doc(A, "duos/duoAB"), { members: ["alice"] })));
await t("semanas", assertSucceeds(setDoc(doc(B, "duos/duoAB/weeks/2026-W41"), { forfeit: "Masajes" }, { merge: true })));
await t("aiUsage cerrado", assertFails(getDoc(doc(A, "duos/duoAB/aiUsage/alice_x"))));
await t("otros paths cerrados", assertFails(setDoc(doc(A, "otra/cosa"), { a: 1 })));

// ---------- Migración del dúo viejo (duos/main) ----------
await env.withSecurityRulesDisabled(async (ctx) => {
  await setDoc(doc(ctx.firestore(), "duos/main"), { members: ["eve", "frank"] });
});
const E = db("eve"), F = db("frank");
await t("eve se registra", assertSucceeds(register(E, "eve", "EVE555", "Eve")));
await t("eve se asigna su dúo viejo", assertSucceeds(updateDoc(doc(E, "users/eve"), { duoId: "main" })));
await t("frank se registra", assertSucceeds(register(F, "frank", "FRANK6", "Frank")));
await t("frank no se asigna un dúo ajeno", assertFails(updateDoc(doc(F, "users/frank"), { duoId: "duoAB" })));
await t("frank se asigna su dúo viejo", assertSucceeds(updateDoc(doc(F, "users/frank"), { duoId: "main" })));
await t("frank lee el dúo viejo", assertSucceeds(getDoc(doc(F, "duos/main"))));

await env.cleanup();
console.log(`\n${pass} ok, ${fail} fail`);
process.exit(fail ? 1 : 0);
