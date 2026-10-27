import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { isSafeLeafName, PROTOCOL_VERSION } from "../src/index.js";

const loadJson = async (relativePath) =>
  JSON.parse(await readFile(new URL(relativePath, import.meta.url), "utf8"));

test("v1 transfer offer remains compatible with the published schemas", async () => {
  const [envelopeSchema, offerSchema, fixture] = await Promise.all([
    loadJson("../schema/envelope.schema.json"),
    loadJson("../schema/transfer-offer.schema.json"),
    loadJson("../fixtures/transfer-offer.v1.json")
  ]);
  const ajv = new Ajv2020({ allErrors: true });
  addFormats(ajv);

  assert.equal(fixture.protocolVersion, PROTOCOL_VERSION);
  assert.equal(ajv.compile(envelopeSchema)(fixture), true);
  assert.equal(ajv.compile(offerSchema)(fixture.payload), true);
});

test("portable filename checks reject traversal and Windows device names", () => {
  for (const name of ["../secret.txt", "folder/file.txt", "folder\\file.txt", "CON", "photo.jpg. "]) {
    assert.equal(isSafeLeafName(name), false, name);
  }
  for (const name of ["photo.jpg", "résumé 2026.pdf", "hello.txt"]) {
    assert.equal(isSafeLeafName(name), true, name);
  }
});
