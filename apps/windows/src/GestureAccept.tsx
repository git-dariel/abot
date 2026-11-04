import { useEffect, useRef, useState } from "react";
import { FilesetResolver, GestureRecognizer } from "@mediapipe/tasks-vision";
import { GestureSequence } from "./gesture-sequence.mjs";

type GestureAcceptProps = {
  onAccept: () => void;
  intro?: string;
  armed?: string;
  action?: string;
};

export function GestureAccept({
  onAccept,
  intro = "Trusted incoming offer received. Starting the camera: show a fist, then open your palm to accept.",
  armed = "FIST LOCKED - open your palm to accept this file.",
  action = "accept this file",
}: GestureAcceptProps) {
  const [enabled, setEnabled] = useState(true);
  const [status, setStatus] = useState(intro);
  const video = useRef<HTMLVideoElement>(null);
  const accept = useRef(onAccept);
  accept.current = onAccept;

  useEffect(() => {
    if (!enabled) return;
    let disposed = false;
    let stream: MediaStream | undefined;
    let recognizer: GestureRecognizer | undefined;
    let timer = 0;
    const sequence = new GestureSequence();
    const stop = () => {
      window.clearTimeout(timer);
      stream?.getTracks().forEach(track => track.stop());
      recognizer?.close();
      recognizer = undefined;
      if (video.current) video.current.srcObject = null;
    };
    const leave = () => {
      disposed = true;
      stop();
      setEnabled(false);
      setStatus("Gesture acceptance stopped. Enable it again when ready.");
    };
    const visibility = () => { if (document.hidden) leave(); };
    document.addEventListener("visibilitychange", visibility);
    const run = async () => {
      try {
        setStatus("Starting local gesture recognition...");
        stream = await navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 }, audio: false });
        if (disposed) { stop(); return; }
        const element = video.current;
        if (!element) throw new Error("Preview unavailable");
        element.srcObject = stream;
        await element.play();
        const files = await FilesetResolver.forVisionTasks("/gesture/wasm");
        if (disposed) { stop(); return; }
        recognizer = await GestureRecognizer.createFromOptions(files, {
          baseOptions: { modelAssetPath: "/gesture/gesture_recognizer.task", delegate: "CPU" },
          runningMode: "VIDEO", numHands: 2,
        });
        if (disposed) { stop(); return; }
        window.addEventListener("blur", leave);
        let previousFrame = -1;
        const tick = () => {
          if (disposed || !recognizer) return;
          try {
            if (element.readyState >= 2 && element.currentTime !== previousFrame) {
              previousFrame = element.currentTime;
              const result = recognizer.recognizeForVideo(element, performance.now());
              const gesture = result.gestures[0]?.[0];
              const fire = sequence.observe(gesture?.categoryName ?? null, gesture?.score ?? 0, result.landmarks.length, performance.now());
              const confidence = Math.round((gesture?.score ?? 0) * 100);
              setStatus(
                sequence.armed
                  ? armed
                  : gesture?.categoryName === "Closed_Fist"
                    ? `Fist found (${confidence}%). Hold it steady.`
                    : result.landmarks.length
                      ? `Hand found, but no clear fist yet (${confidence}%).`
                      : "Camera ready — hold one closed fist in view.",
              );
              if (fire) {
                disposed = true;
                stop();
                setEnabled(false);
                accept.current();
                return;
              }
            }
            timer = window.setTimeout(tick, 100);
          } catch { fail(); }
        };
        tick();
      } catch { fail(); }
    };
    const fail = () => {
      if (disposed) return;
      disposed = true;
      stop();
      setEnabled(false);
      setStatus(`Camera or gesture model unavailable. Check camera permission or use the button to ${action}.`);
    };
    void run();
    return () => {
      disposed = true;
      document.removeEventListener("visibilitychange", visibility);
      window.removeEventListener("blur", leave);
      stop();
    };
  }, [enabled, action, armed]);

  return <div className="gesture-panel">
    <p>{status}</p>
    {enabled && <video ref={video} muted playsInline aria-label="Local hand gesture camera preview" />}
    <button type="button" className="secondary" onClick={() => setEnabled(value => !value)}>
      {enabled ? "Stop release camera" : "Restart release camera"}
    </button>
    <small>Hold each pose briefly. Camera frames stay on this PC.</small>
  </div>;
}
