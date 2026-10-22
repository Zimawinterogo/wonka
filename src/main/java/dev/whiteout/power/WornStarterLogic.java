package dev.whiteout.power;
/** Mechanical wear: five successive starter stages, short windows, an accelerating flywheel and a full reset on a misfire. */
public final class WornStarterLogic {
 /** Successful pulls required to start the old generator. This is NOT the number of allowed misses (a miss resets progress). */
 public static final int PULLS=5;
 private static int stage(int progress){return Math.max(0,Math.min(PULLS-1,progress));}
 // The flywheel gets quicker with every stage: period 46,41,36,31,26 ticks.
 public static int period(int progress){return 46-5*stage(progress);}
 // The good window starts at 25,22,19,16,13 and narrows from 8 to 4 ticks.
 public static int windowStart(int progress){return 25-3*stage(progress);}
 public static int windowEnd(int progress){return windowStart(progress)+8-stage(progress);}
 public static int phase(long ticks,int progress){return (int)Math.floorMod(ticks,period(progress));}
 public static boolean hit(long ticks,int progress){int p=phase(ticks,progress);return ticks>=0&&p>=windowStart(progress)&&p<windowEnd(progress);}
 // A missed pull loses compression completely; a hit advances exactly one stage.
 public static int next(int progress,boolean success){return success?Math.min(PULLS,progress+1):0;}
 private WornStarterLogic(){}
}
