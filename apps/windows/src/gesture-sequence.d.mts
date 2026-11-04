export class GestureSequence {
  constructor(firstPose?: "Closed_Fist" | "Open_Palm", secondPose?: "Closed_Fist" | "Open_Palm");
  armed: boolean;
  reset(): void;
  observe(name: string | null, confidence: number, hands: number, timeMs: number): boolean;
}
