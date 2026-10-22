package dev.whiteout.power;

/** One update per second; thawing never depends on discovery or recovery timers. */
public final class FrostProgress {
 private FrostProgress(){}
 public static int advance(int age,boolean storm){
  return Math.max(0,Math.min(76,age+(storm?1:-1)));
 }
}
