// A fresh instance is scoped to one reviewed offer and one camera session.
export class GestureSequence {
  firstPose;
  secondPose;
  armed = false;
  fired = false;
  pose = null;
  since = 0;
  last = -1;
  armedAt = 0;

  constructor(firstPose = 'Closed_Fist', secondPose = 'Open_Palm') {
    this.firstPose = firstPose;
    this.secondPose = secondPose;
  }

  reset() {
    this.armed = false;
    this.fired = false;
    this.pose = null;
    this.last = -1;
  }

  observe(name, confidence, hands, timeMs) {
    if (this.fired) return false;
    if (this.last >= 0 && (timeMs <= this.last || timeMs - this.last > 3000)) this.reset();
    this.last = timeMs;
    if (this.armed && timeMs - this.armedAt > 12000) this.reset();
    if (hands !== 1 || confidence < 0.55 || !['Open_Palm', 'Closed_Fist'].includes(name)) {
      this.pose = null;
      return false;
    }
    if (this.pose !== name) { this.pose = name; this.since = timeMs; }
    if (timeMs - this.since < 350) return false;
    if (!this.armed && name === this.firstPose) {
      this.armed = true;
      this.armedAt = timeMs;
      this.pose = null;
    } else if (this.armed && name === this.secondPose) {
      this.fired = true;
      return true;
    }
    return false;
  }
}
