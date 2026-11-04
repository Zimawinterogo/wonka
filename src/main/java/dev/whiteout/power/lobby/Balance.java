package dev.whiteout.power.lobby;

import dev.whiteout.Store;
import net.minecraft.world.World;
import java.util.*;

public record Balance(String preset,int newFuel,int oldFuel,int lampMinutes,int chargeMinutes,int torchMinutes,int fusePercent,int fuseSeconds,int stormSeconds,int snowSeconds){
 public interface Access{Balance whiteout$balance();void whiteout$balance(Balance value);}
 public Balance{
  if(!List.of("easy","normal","hard","custom").contains(preset))throw new IllegalArgumentException("preset");
  range(newFuel,10,500);range(oldFuel,10,500);range(lampMinutes,1,120);range(chargeMinutes,1,120);range(torchMinutes,5,180);
  range(fusePercent,0,100);range(fuseSeconds,1,300);range(stormSeconds,10,3600);range(snowSeconds,10,3600);
 }
 private static void range(int n,int min,int max){if(n<min||n>max)throw new IllegalArgumentException("balance outside range");}
 public static Balance preset(String id){return switch(id){
  case "easy"->new Balance(id,70,70,15,6,30,5,10,300,480);
  case "normal"->new Balance(id,100,100,7,15,20,10,8,240,360);
  case "hard"->new Balance(id,150,170,4,25,10,18,7,160,240);
  default->throw new IllegalArgumentException("preset");};}
 public static Balance get(Store.Data data){return (Object)data instanceof Access a?a.whiteout$balance():null;}
 public static Balance get(World world){return world==null||world.getServer()==null?null:get(Store.get(world.getServer()).data);}
 static void set(Store store,Balance value)throws java.io.IOException{
  var access=(Access)(Object)store.data;var old=access.whiteout$balance();access.whiteout$balance(value);
  try{store.save();}catch(java.io.IOException|RuntimeException ex){access.whiteout$balance(old);throw ex;}
 }
}
