import test from 'node:test';
import assert from 'node:assert/strict';
import { GestureSequence } from '../src/gesture-sequence.mjs';

const hold = (gate, pose, start, confidence = 0.95, hands = 1) => {
  let fired = false;
  for (let time = start; time <= start + 700; time += 100) fired ||= gate.observe(pose, confidence, hands, time);
  return fired;
};
test('accept requires fist then palm, fires once', () => {
  const gate = new GestureSequence();
  assert.equal(hold(gate, 'Open_Palm', 0), false);
  assert.equal(hold(gate, 'Closed_Fist', 800), false);
  assert.equal(hold(gate, 'Open_Palm', 1600), true);
  assert.equal(hold(gate, 'Open_Palm', 2400), false);
});
test('low confidence, two hands, brief poses and interruptions cannot accept', () => {
  for (const [confidence, hands] of [[0.5, 1], [0.95, 2]]) {
    const gate = new GestureSequence();
    hold(gate, 'Closed_Fist', 0, confidence, hands);
    assert.equal(hold(gate, 'Open_Palm', 800), false);
  }
  const gate = new GestureSequence();
  gate.observe('Closed_Fist', 0.95, 1, 0);
  assert.equal(hold(gate, 'Open_Palm', 100), false);
  hold(gate, 'Closed_Fist', 1000);
  assert.equal(hold(gate, 'Open_Palm', 5000), false);
});
test('new offer or camera session requires a fresh sequence', () => {
  const gate = new GestureSequence();
  hold(gate, 'Closed_Fist', 0);
  gate.reset();
  assert.equal(hold(gate, 'Open_Palm', 800), false);
});
test('slow camera inference frames can still complete the release', () => {
  const gate = new GestureSequence();
  assert.equal(gate.observe('Closed_Fist', 0.75, 1, 0), false);
  assert.equal(gate.observe('Closed_Fist', 0.75, 1, 1200), false);
  assert.equal(gate.observe('Open_Palm', 0.75, 1, 2400), false);
  assert.equal(gate.observe('Open_Palm', 0.75, 1, 3600), true);
});
test('outgoing grab requires palm then fist and fires once', () => {
  const gate = new GestureSequence('Open_Palm', 'Closed_Fist');
  assert.equal(hold(gate, 'Closed_Fist', 0), false);
  assert.equal(hold(gate, 'Open_Palm', 800), false);
  assert.equal(hold(gate, 'Closed_Fist', 1600), true);
  assert.equal(hold(gate, 'Closed_Fist', 2400), false);
});
