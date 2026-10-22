package dev.whiteout.power;

/** Pure, allocation-free billboard maths. Wind deliberately is not an input. */
public final class GroundSnowBillboard {
 private GroundSnowBillboard(){}
 /** Cylindrical near the horizon; smoothly opens onto the ground towards overhead views. */
 public static double tilt(double pitchDegrees){
  double p=Math.max(-90,Math.min(90,pitchDegrees));
  double t=Math.max(0,Math.min(1,(Math.abs(p)-25)/65));
  return Math.toRadians(p)*t*t*(3-2*t);
 }
 public static int softLight(int packed){
  // Keep sky lighting, but don't let white translucent snow become black in unlit areas.
  return (packed&0xffff0000)|Math.max(packed&0xffff,6<<4);
 }
}
