package dev.whiteout.power;

/** Deterministic part of the server-side electrical circuit and objective rules.
 * Does not touch chunks or Minecraft objects, and can be unit-tested with javac. */
public final class GridStatePolicy {
 private GridStatePolicy() {}
 public static boolean supplied(boolean panelBound,boolean generatorBound,
                                boolean panelSeen,boolean generatorSeen,
                                boolean fusePresent,boolean fuseBurnt,
                                boolean generatorRunning,boolean generatorFueled,
                                boolean fuseBurnPending,boolean stopPending){
  return panelBound&&generatorBound&&panelSeen&&generatorSeen
   &&fusePresent&&!fuseBurnt&&generatorRunning&&generatorFueled
   &&!fuseBurnPending&&!stopPending;
 }
 /** Each configured fixture, not a hard-coded count, must be present before completing. */
 public static boolean questComplete(boolean power,int registeredFixtures,boolean allVerified){
  return power&&registeredFixtures>0&&allVerified;
 }
}
