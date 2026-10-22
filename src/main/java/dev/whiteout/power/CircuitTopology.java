package dev.whiteout.power;

/** Small dependency-free rules used when assigning blocks to independent circuits. */
public final class CircuitTopology {
 private CircuitTopology() {}
 public static boolean validName(String value) {
  return value != null && !value.equals("main") && value.matches("[a-z0-9_-]{1,32}");
 }
 /** Circuit ids are isolated: their generators and panels cannot be shared. */
 public static boolean canClaim(String target, String existing) {
  return existing == null || target != null && target.equals(existing);
 }
}
