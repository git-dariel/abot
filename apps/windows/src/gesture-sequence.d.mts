export class GestureSequence {
  armed: boolean;
  reset(): void;
  observe(name: string | null, confidence: number, hands: number, timeMs: number): boolean;
}
