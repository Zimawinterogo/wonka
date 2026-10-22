package dev.whiteout.power;

/** Client-only transient motor envelope; never persisted or used for gameplay power. */
public final class GeneratorMotion {
 private boolean initialized,running;
 private int delay,loopAge,ticks;
 private float speed,previous;

 public boolean tick(boolean active,boolean worn){
  boolean started=initialized&&!running&&active;
  // Keep the existing ignition / loop-audio handoff exactly as it was.
  if(active&&!running){delay=started?(worn?84:24):0;loopAge=0;}
  initialized=true;running=active;previous=speed;ticks++;
  // A cubic easing curve gives a still motor a gentle initial movement and
  // lets a switched-off engine coast to rest instead of snapping to zero.
  speed=Math.max(0,Math.min(1,speed+(active?1f/16f:-1f/18f)));
  if(active){if(delay>0)delay--;else loopAge++;}else{delay=0;loopAge=0;}
  return started;
 }
 public boolean loopReady(){return running&&delay==0;}
 public float loopVolume(boolean worn){return !loopReady()?0:Math.min(1,loopAge/(worn?40f:12f));}
 public float amount(float delta){
  float progress=previous+(speed-previous)*Math.max(0,Math.min(1,delta));
  return progress*progress*(3-2*progress);
 }
 public float indicator(boolean worn,float delta,long seed){
  double phase=(ticks+delta)*.37+(seed&255);
  return 1-amount(delta)*(float)((worn?.075:.025)*(1+Math.sin(phase)*Math.sin(phase*1.73)));
 }
 public double vibration(boolean worn,float delta,long seed){
  double phase=(ticks+delta)*2.7+(seed&255);
  return (Math.sin(phase)*.78+Math.sin(phase*1.81)*.22)*(worn?.006:.0025)*amount(delta);
 }
 /** Tiny render-only engine rocking: degrees, not a rotation of separate mesh parts. */
 public float rock(boolean worn,float delta,long seed){
  double phase=(ticks+delta)*1.31+((seed>>>8)&255);
  return (float)((Math.sin(phase)*.8+Math.sin(phase*1.69)*.2)*(worn?.13:.055)*amount(delta));
 }
 /** Smoothly remove cosmetic motion before switching to the static far LOD. */
 public static float nearFade(double cameraDistanceSquared){
  if(cameraDistanceSquared<=18*18)return 1;
  if(cameraDistanceSquared>=24*24)return 0;
  float p=(float)((Math.sqrt(cameraDistanceSquared)-18)/6);
  return 1-p*p*(3-2*p);
 }
}
