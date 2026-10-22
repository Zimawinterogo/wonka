package dev.whiteout.power;

/** Pure electrical rules shared by server wiring and standalone tests. */
public final class CircuitRules {
 private CircuitRules() {}
 public static boolean hasPower(boolean panelBound,boolean generatorBound,boolean panelPresent,
  boolean generatorPresent,boolean fusePresent,boolean fuseBurnt,boolean generatorRunning,int fuel){
  return panelBound&&generatorBound&&panelPresent&&generatorPresent&&fusePresent&&!fuseBurnt&&generatorRunning&&fuel>0;
 }
 public static boolean entered(boolean configured,boolean fired,boolean armed,boolean inside){
  return configured&&!fired&&armed&&inside;
 }
 public static boolean rearm(boolean configured,boolean fired,boolean inside){
  return configured&&!fired&&!inside;
 }
}
