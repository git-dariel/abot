export const PROTOCOL_VERSION = "1.0";

export const transferStates = Object.freeze([
  "IDLE",
  "FILE_SELECTED",
  "GESTURE_ARMED",
  "TRANSFER_OFFERED",
  "AWAITING_REMOTE_CONFIRMATION",
  "ACCEPTED",
  "TRANSFERRING",
  "VERIFYING",
  "COMPLETED",
  "FAILED",
  "CANCELLED"
]);

export const messageTypes = Object.freeze([
  "transfer.offer",
  "transfer.accept",
  "transfer.reject",
  "transfer.cancel",
  "transfer.progress",
  "transfer.complete",
  "transfer.failed"
]);

/**
 * Rejects values that are not safe portable leaf filenames. This is a protocol
 * preflight only; receivers must enforce their own filesystem rules too.
 * @param {string} value
 */
export function isSafeLeafName(value) {
  if (value.length === 0 || value.length > 255 || value === "." || value === "..") return false;
  if (/[\\/:*?"<>|\u0000-\u001f]/u.test(value)) return false;
  if (/[. ]$/u.test(value)) return false;

  const stem = value.split(".", 1)[0].toUpperCase();
  return !/^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$/u.test(stem);
}
