import dev.whiteout.power.LakeStoryPolicy;
import java.util.HashSet;

public class LakeStoryPolicyTest {
 static int assertions;
 static void check(boolean value){assertions++;if(!value)throw new AssertionError("assertion "+assertions);}
 public static void main(String[] args){
  check(LakeStoryPolicy.inStart(8381,72,8366));
  for(int dx:new int[]{-3,3})for(int dy:new int[]{-3,3})for(int dz:new int[]{-3,3})check(LakeStoryPolicy.inStart(8381+dx,72+dy,8366+dz));
  for(double[] p:new double[][]{{8384.001,72,8366},{8381,75.001,8366},{8381,72,8369.001},{8408,72,8380},{8411,72,8383},{8407,71,8382}})check(!LakeStoryPolicy.inStart(p[0],p[1],p[2]));
  for(int stage=0;stage<12;stage++)for(int pulls=0;pulls<5;pulls++){
   check(LakeStoryPolicy.afterPull(stage,false,pulls)==stage);
   check(LakeStoryPolicy.afterPull(stage,true,pulls)==(stage==3&&pulls>=3?4:stage));
   check(LakeStoryPolicy.afterPanel(stage,false)==stage);
  }
  check(LakeStoryPolicy.afterPanel(4,true)==5);
  for(int stage:new int[]{0,1,2,3,5,6,8,9,10,11})check(LakeStoryPolicy.afterPanel(stage,true)==stage);
  int[] path={0,1,2,3,4,5,6,8,9,10,11};
  for(int i=1;i<path.length;i++)check(LakeStoryPolicy.transition(path[i-1],path[i]));
  for(int i=0;i<12;i++){check(!LakeStoryPolicy.transition(i,i));check(!LakeStoryPolicy.transition(i,0));}
  check(!LakeStoryPolicy.transition(3,5));check(!LakeStoryPolicy.transition(6,9));
  var goals=new HashSet<String>();for(int stage=0;stage<12;stage++){String goal=LakeStoryPolicy.objective(stage);if(!goal.isEmpty()){goals.add(goal);check(!LakeStoryPolicy.description(goal).isEmpty());}}
  check(goals.size()==4);check(LakeStoryPolicy.objective(10).isEmpty());check(LakeStoryPolicy.objective(11).isEmpty());
  check(LakeStoryPolicy.opacity(0)==0);check(LakeStoryPolicy.opacity(.5)==1);
  check(LakeStoryPolicy.descriptionOpacity(.5)==0);check(LakeStoryPolicy.descriptionOpacity(1.2)==1);
  check(LakeStoryPolicy.opacity(8.3)==1);check(LakeStoryPolicy.opacity(9.5)==0);
  for(int fps:new int[]{30,60,144,240}){
   double previous=0;
   for(int frame=0;frame<=fps/2;frame++){double a=LakeStoryPolicy.opacity((double)frame/fps);check(a>=previous);previous=a;}
   check(LakeStoryPolicy.finished(10));check(!LakeStoryPolicy.finished(9));
  }
  check(LakeStoryPolicy.shouldRestore(false,true,false));
  check(!LakeStoryPolicy.shouldRestore(true,true,false)); // idempotent reset
  check(!LakeStoryPolicy.shouldRestore(false,false,false)); // player replacement
  check(!LakeStoryPolicy.shouldRestore(false,true,true)); // never overwrite a BE
  System.out.println("LakeStoryPolicyTest: "+assertions+" assertions passed");
 }
}
