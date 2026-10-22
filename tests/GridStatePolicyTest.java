import dev.whiteout.power.GridStatePolicy;
public class GridStatePolicyTest {
 static int checked;
 static void check(boolean expected,boolean actual){checked++;if(expected!=actual)throw new AssertionError("Case "+checked);}
 public static void main(String[] args){
  // All combinations of panel/generator links, device observations, fuse state,
  // running/fueled state, and the two pending emergency actions (1024 cases).
  for(int mask=0;mask<1024;mask++){
   boolean[] v=new boolean[10];for(int j=0;j<10;j++)v[j]=(mask&(1<<j))!=0;
   boolean expected=v[0]&&v[1]&&v[2]&&v[3]&&v[4]&&!v[5]&&v[6]&&v[7]&&!v[8]&&!v[9];
   check(expected,GridStatePolicy.supplied(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9]));
  }
  for(int count=0;count<20;count++)for(boolean power:new boolean[]{false,true})
   for(boolean verified:new boolean[]{false,true})
    check(power&&count>0&&verified,GridStatePolicy.questComplete(power,count,verified));
  System.out.println(checked+" executed GridStatePolicy assertions passed");
 }
}
