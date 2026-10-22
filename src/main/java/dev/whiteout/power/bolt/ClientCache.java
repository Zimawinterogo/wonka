package dev.whiteout.power.bolt;

import java.util.HashMap;
import java.util.Map;

/** Client copy of nearby bolts. No Minecraft client imports: lives in common code. Client thread only. */
final class ClientCache {
 private ClientCache(){}
 record Entry(boolean inner,boolean locked,long changedAt){}
 static String dimension="";
 static final Map<Long,Entry> MAP=new HashMap<>();
 static void clear(){dimension="";MAP.clear();}
 static void apply(String dim,String text){
  Map<Long,Entry> old=new HashMap<>(MAP);
  MAP.clear();dimension=dim;
  if(text.isEmpty())return;
  long now=System.currentTimeMillis();
  for(String line:text.split("\n")){
   String[] p=line.split(",");if(p.length!=5)continue;
   try{
    long key=net.minecraft.util.math.BlockPos.asLong(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));
    boolean inner=p[3].equals("1"),locked=p[4].equals("1");
    Entry prev=old.get(key);
    MAP.put(key,new Entry(inner,locked,prev!=null&&prev.locked==locked?prev.changedAt:(prev==null?0L:now)));
   }catch(NumberFormatException ignored){}
  }
 }
}
