import dev.whiteout.power.ForestCandidatePolicy;
public class ForestCandidatePolicyTest {
 private static int checks;
 static void test(boolean expected,boolean actual){checks++;if(expected!=actual)throw new AssertionError("Forest evidence case "+checks);}
 public static void main(String[] args){
  for(int h=0;h<42;h++)test(Math.abs(h-25)<=2||Math.abs(h-31)<=2,ForestCandidatePolicy.expectedHeight(h));
  for(int branches=0;branches<=12;branches++)for(int levels=0;levels<=4;levels++)for(int foliage:new int[]{0,1,30,64,65,180,550}){
   boolean expected=(branches>=6&&levels>=2)||(branches>=3&&foliage>=65);
   test(expected,ForestCandidatePolicy.stampEvidence(branches,levels,foliage));
  }
  // Bare 31-block log pillar remains untouched. True partial stamp recognized.
  test(false,ForestCandidatePolicy.stampEvidence(0,0,0));
  test(true,ForestCandidatePolicy.stampEvidence(6,2,0));
  test(false,ForestCandidatePolicy.stampEvidence(6,1,0));
  test(true,ForestCandidatePolicy.stampEvidence(3,1,65));
  System.out.println(checks+" executed forest candidate assertions passed");
 }
}
