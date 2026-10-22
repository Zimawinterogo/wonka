package dev.whiteout.power;

/** Conservative recognition of damaged WHITEOUT spruce stamps.
 * A bare player-made spruce pillar is never a valid candidate. */
public final class ForestCandidatePolicy {
 private ForestCandidatePolicy() {}
 public static boolean expectedHeight(int height){
  return Math.abs(height-25)<=2||Math.abs(height-31)<=2;
 }
 public static boolean stampEvidence(int branches,int distinctBranchLevels,int leaves){
  return (branches>=6&&distinctBranchLevels>=2)||(branches>=3&&leaves>=65);
 }
}
