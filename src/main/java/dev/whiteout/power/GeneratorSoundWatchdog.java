package dev.whiteout.power;

/**
 * Recovery guard for a generator loop that was registered by Whiteout but
 * vanished from Minecraft's actual audio mixer (for example after reconnect).
 * All values are client ticks. Pure Java so it can be tested without Fabric.
 */
public final class GeneratorSoundWatchdog {
 private static final int STARTUP_GRACE_TICKS=60;
 private static final int MISSING_TICKS=30;
 private int age,missing;

 public boolean shouldRecover(boolean listenerNear,boolean playing,boolean paused){
  if(age<Integer.MAX_VALUE)age++;
  if(!listenerNear||paused||playing){missing=0;return false;}
  if(missing<MISSING_TICKS)missing++;
  return age>=STARTUP_GRACE_TICKS&&missing>=MISSING_TICKS;
 }
}
