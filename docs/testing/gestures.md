# Downloads and gesture acceptance

## Implemented

- Windows resolves the OS Downloads folder and creates it if missing. No destination picker is required. Existing filenames still fail safely rather than being overwritten.
- ABOT Gallery queries only Android MediaStore photos and videos covered by the user's permission grant. Users may select and deselect mixed media, then Continue to the local palm-to-fist Grab page. Receiver discovery starts only from the Settings toggle.
- The Windows receiver starts when the desktop app launches. For an identity-approved pending offer, the fist-to-palm acceptance camera starts automatically; buttons remain available for accessibility and recovery.
- Android: select one or more documents with the native picker, or mixed media in ABOT Gallery. Selection confirmation opens the Grab page automatically. Stable open palm followed by stable closed fist arms the batch; every file remains a separate consent-bound offer.
- Windows: review the incoming offer, approve pairing if needed, and enable gesture acceptance. Stable closed fist followed by stable open palm accepts the reviewed offer. A transfer-ID check rejects stale decisions.
- Both poses require confidence >= 0.55, exactly one detected hand, and a 350 ms hold. The second pose must arrive within 12 seconds. Frame gaps longer than 3 seconds reset the sequence, allowing slower phones to retain deliberate poses without accepting stale input; repeated frames cannot re-trigger a completed action.
- Android displays the detected pose/confidence, vibrates when the palm locks and grab completes, and reports when the offer reaches Windows or the connection fails. Windows displays camera-ready, fist-confidence, and fist-locked states.
- Cameras stop on cancellation, leaving the screen, or completion. Windows also stops on focus loss after initialization. Camera permission/model errors retain ordinary button controls.
- Model and runtime are bundled locally. No camera data is sent over the network. This is gesture intent on each device, not tracking one hand between cameras.

## Physical-device checklist (pending)

1. Open the Windows app and confirm its receiver starts automatically. Reconnect or scan QR once if this installation has no provisioned trusted credential.
2. On Android, open ABOT Gallery, grant full or selected-photo access, and open a small uniquely named photo. Verify the photo preview and front-camera inset appear automatically.
3. Verify a fist alone, a brief palm, and two hands do not offer the photo. Hold palm then fist; verify exactly one offer appears.
4. Verify Downloads has no received file before Windows accepts. Hold a fist then open the palm at Windows; verify completion and matching SHA-256.
5. Repeat with button acceptance, then rejection. Rejection must create no received file.
6. Test denied/partial photo permission, denied camera permission, unavailable camera, back/cancel, rotation, app switching, and repeated gestures. No action may occur after leaving the gesture screen.
7. Try an existing filename; confirm it is not overwritten. Try a redirected Downloads folder if available.

Unit tests exercise gesture event sequences, not camera recognition accuracy. Native WebView camera permission and recognition performance require testing on the user's hardware.
