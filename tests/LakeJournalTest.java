package dev.whiteout.power;

import com.google.gson.Gson;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;

/** Real Minecraft registry/blockstate round-trips under Fabric's transformed game classes. */
public final class LakeJournalTest {
 public static void main(String[] args){
  SharedConstants.createGameVersion();Bootstrap.initialize();
  Gson gson=new Gson();int checked=0;
  for(int mask=0;mask<32;mask++){
   var state=Blocks.GLASS_PANE.getDefaultState().with(Properties.EAST,(mask&1)!=0)
    .with(Properties.WEST,(mask&2)!=0).with(Properties.NORTH,(mask&4)!=0)
    .with(Properties.SOUTH,(mask&8)!=0).with(Properties.WATERLOGGED,(mask&16)!=0);
   var snapshot=LakeBlockJournal.Snapshot.of(state);
   var reloaded=gson.fromJson(gson.toJson(snapshot),LakeBlockJournal.Snapshot.class);
   if(!state.equals(reloaded.state()))throw new AssertionError("lost pane state "+mask);
   checked++;
  }
  var invalid=LakeBlockJournal.Snapshot.of(Blocks.GLASS_PANE.getDefaultState());
  invalid.properties.put("east","invalid");
  try{invalid.state();throw new AssertionError("accepted corrupt journal");}catch(IllegalStateException expected){}
  var journal=new LakeBlockJournal();
  for(int y=80;y<=81;y++){
   var change=new LakeBlockJournal.Change();change.x=8410;change.y=y;change.z=8383;change.group="glass";
   change.before=LakeBlockJournal.Snapshot.of(Blocks.GLASS_PANE.getDefaultState().with(Properties.EAST,y==80));
   change.after=LakeBlockJournal.Snapshot.of(Blocks.AIR.getDefaultState());journal.changes.add(change);
  }
  var restored=gson.fromJson(gson.toJson(journal),LakeBlockJournal.class);
  if(restored.changes.size()!=2||restored.changes.get(0).before.state().equals(restored.changes.get(1).before.state()))throw new AssertionError("windows conflated");
  System.out.println("PASS LakeJournalTest: "+checked+" real pane states, distinct windows, corrupt property rejected");
 }
}
