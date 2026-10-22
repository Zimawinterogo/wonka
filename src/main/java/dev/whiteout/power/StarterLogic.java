package dev.whiteout.power;
/** Shared timing rules: progressively faster pulls and narrower target windows. */
public final class StarterLogic {
 public static final int PULLS=3;
 private static int stage(int progress){return Math.max(0,Math.min(PULLS-1,progress));}
 public static int period(int progress){return 48-6*stage(progress);}
 public static int windowStart(int progress){return new int[]{28,25,22}[stage(progress)];}
 public static int windowEnd(int progress){return new int[]{39,33,27}[stage(progress)];}
 public static int phase(long elapsed,int progress){return (int)Math.floorMod(elapsed,period(progress));}
 public static boolean hit(long elapsed,int progress){int p=phase(elapsed,progress);return elapsed>=0&&p>=windowStart(progress)&&p<windowEnd(progress);}
 public static int next(int progress,boolean success){return success?Math.min(PULLS,progress+1):Math.max(0,progress-1);}
 private StarterLogic(){}
}
