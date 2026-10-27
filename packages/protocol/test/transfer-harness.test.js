import assert from "node:assert/strict";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { createWriteStream } from "node:fs";
import { mkdtemp, readFile, rename, rm } from "node:fs/promises";
import { createServer, request } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { isSafeLeafName } from "../src/index.js";

const sendRequest = (port, path, body = Buffer.alloc(0), headers = {}) =>
  new Promise((resolve, reject) => {
    const outgoing = request(
      { hostname: "127.0.0.1", port, path, method: "POST", headers: { "content-length": body.length, ...headers } },
      (response) => {
        const chunks = [];
        response.on("data", (chunk) => chunks.push(chunk));
        response.on("end", () => resolve({ status: response.statusCode, body: Buffer.concat(chunks).toString("utf8") }));
      }
    );
    outgoing.on("error", reject);
    outgoing.end(body);
  });

test("synthetic peer requires acceptance and finalizes verified streamed bytes", async (context) => {
  const destination = await mkdtemp(join(tmpdir(), "airbridge-transfer-"));
  context.after(() => rm(destination, { recursive: true, force: true }));

  let offer;
  let accepted = false;
  const uploadToken = randomUUID();
  const server = createServer(async (incoming, response) => {
    try {
      if (incoming.url === "/offer") {
        const chunks = [];
        for await (const chunk of incoming) chunks.push(chunk);
        const candidate = JSON.parse(Buffer.concat(chunks).toString("utf8"));
        if (!isSafeLeafName(candidate.name) || candidate.size > 2 * 1024 * 1024) {
          response.writeHead(400).end();
          return;
        }
        offer = candidate;
        response.writeHead(202).end();
        return;
      }
      if (incoming.url === "/accept" && offer) {
        accepted = true;
        response.writeHead(200, { "content-type": "text/plain" }).end(uploadToken);
        return;
      }
      if (incoming.url === "/upload") {
        if (!accepted || incoming.headers.authorization !== `Bearer ${uploadToken}`) {
          response.writeHead(403).end();
          return;
        }

        const temporaryPath = join(destination, `.${offer.name}.part`);
        const finalPath = join(destination, offer.name);
        const output = createWriteStream(temporaryPath, { flags: "wx" });
        const digest = createHash("sha256");
        let received = 0;
        for await (const chunk of incoming) {
          received += chunk.length;
          digest.update(chunk);
          if (!output.write(chunk)) await new Promise((resolve) => output.once("drain", resolve));
        }
        await new Promise((resolve, reject) => output.end((error) => error ? reject(error) : resolve()));
        if (received !== offer.size || digest.digest("hex") !== offer.sha256) {
          await rm(temporaryPath, { force: true });
          response.writeHead(422).end();
          return;
        }
        await rename(temporaryPath, finalPath);
        response.writeHead(201).end();
        return;
      }
      response.writeHead(404).end();
    } catch (error) {
      response.writeHead(500).end(error instanceof Error ? error.message : "unknown error");
    }
  });

  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(() => new Promise((resolve) => server.close(resolve)));
  const address = server.address();
  assert.notEqual(address, null);
  assert.equal(typeof address, "object");
  const port = address.port;

  const bytes = randomBytes(512 * 1024);
  const metadata = Buffer.from(JSON.stringify({
    name: "synthetic.bin",
    size: bytes.length,
    sha256: createHash("sha256").update(bytes).digest("hex")
  }));

  assert.equal((await sendRequest(port, "/offer", metadata, { "content-type": "application/json" })).status, 202);
  assert.equal((await sendRequest(port, "/upload", bytes)).status, 403);
  assert.equal((await sendRequest(port, "/accept")).body, uploadToken);
  assert.equal((await sendRequest(port, "/upload", bytes, { authorization: `Bearer ${uploadToken}` })).status, 201);
  assert.deepEqual(await readFile(join(destination, "synthetic.bin")), bytes);
});
