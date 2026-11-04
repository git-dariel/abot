import { cp, mkdir, readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const target = new URL('../public/gesture/', import.meta.url);
const model = new URL('../../../assets/gesture/gesture_recognizer.task', import.meta.url);
const hash = createHash('sha256').update(await readFile(model)).digest('hex');
if (hash !== '97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482') throw new Error('Gesture model checksum mismatch');
await mkdir(target, { recursive: true });
await cp(model, new URL('gesture_recognizer.task', target));
await cp(fileURLToPath(new URL('../node_modules/@mediapipe/tasks-vision/wasm/', import.meta.url)), fileURLToPath(new URL('wasm/', target)), { recursive: true });
