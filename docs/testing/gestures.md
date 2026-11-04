# Downloads and gesture acceptance

## Implemented

- Windows resolves the OS Downloads folder and creates it if missing. No destination picker is required. Existing filenames still fail safely rather than being overwritten.
- Android: select a document, establish trust, then explicitly open the front camera with **Grab with hand gesture**. Stable open palm followed by stable closed fist offers that file.
- Windows: review the incoming offer, approve pairing if needed, and enable gesture acceptance. Stable closed fist followed by stable open palm accepts the reviewed offer. A transfer-ID check rejects stale decisions.
- Both poses require confidence >= 0.8, exactly one detected hand, and a 600 ms hold. The second pose must arrive within 8 seconds. Long frame interruptions reset the sequence; repeated frames cannot re-trigger a completed action.
- Cameras stop on cancellation, leaving the screen, or completion. Windows also stops on focus loss after initialization. Camera permission/model errors retain ordinary button controls.
- Model and runtime are bundled locally. No camera data is sent over the network. This is gesture intent on each device, not tracking one hand between cameras.

## Physical-device checklist (pending)

1. Start a fresh Windows receiver, reconnect or scan QR, select a small uniquely named Android file.
2. Enable Android gestures. Verify a fist alone, a brief palm, and two hands do not offer the file. Hold palm then fist; verify exactly one offer appears.
3. Verify Downloads has no received file before Windows accepts. Review the metadata, enable the Windows camera, hold fist then palm. Verify completion and matching SHA-256.
4. Repeat with button acceptance, then rejection. Rejection must create no received file.
5. Test denied camera permission, unavailable camera, back/cancel, rotation, app switching, and repeated gestures. No action may occur after leaving the gesture screen.
6. Try an existing filename; confirm it is not overwritten. Try a redirected Downloads folder if available.

Unit tests exercise gesture event sequences, not camera recognition accuracy. Native WebView camera permission and recognition performance require testing on the user's hardware.
