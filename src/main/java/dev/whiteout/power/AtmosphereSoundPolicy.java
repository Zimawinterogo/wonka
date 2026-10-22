package dev.whiteout.power;

/** Stateless, testable volume and smoothing rules for client-only one-shot ambience. */
public final class AtmosphereSoundPolicy {
 private AtmosphereSoundPolicy(){}
 private static float clamp(float value){
  return Float.isFinite(value)?Math.max(0f,Math.min(1f,value)):0f;
 }
 /** Gradually follow storms; roughly two seconds to settle at 20 client ticks/s. */
 public static float smoothStorm(float previous,float observed){
  return clamp(previous)+(clamp(observed)-clamp(previous))*.08f;
 }
 /** Farther rattles are quieter; avoid instantaneous full-volume sounds across a room. */
 public static float creakVolume(float storm,int distance){
  float falloff=Math.max(.45f,1f-Math.max(0,distance-2)*.055f);
  return (.30f+.40f*clamp(storm))*falloff;
 }
 /** Audible static increases with the weather instead of jumping at a threshold. */
 public static float radioVolume(float storm){return .11f+.24f*clamp(storm);}
}
