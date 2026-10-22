package dev.whiteout.power.barricade;

import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/** One item = one cell of boards. A doorway uses two items automatically; sneak to force a single cell. */
public final class BarricadeItem extends BlockItem {
 public BarricadeItem(BarricadeBlock block,Settings settings){super(block,settings);}

 @Override public ActionResult place(ItemPlacementContext ctx){
  PlayerEntity player=ctx.getPlayer();
  if(player==null||player.isSpectator())return ActionResult.FAIL; // dispensers/automation are not supported
  World world=ctx.getWorld();
  boolean creative=player.isCreative();
  int available=creative?64:ctx.getStack().getCount();
  BarricadePlanner.Result r=BarricadePlanner.plan(world,ctx.getBlockPos(),player,player.isSneaking(),available);
  if(r.plan()==null){
   if(player instanceof ServerPlayerEntity sp)sp.sendMessage(Text.translatable(r.errorKey()),true);
   return ActionResult.FAIL;
  }
  BarricadePlanner.Plan plan=r.plan();List<BlockPos> cells=plan.cells();
  // Build every state first and validate them all: placement is all-or-nothing.
  BlockState[] states=new BlockState[cells.size()];
  for(int i=0;i<states.length;i++){
   BarricadeBlock.Part part=states.length==1?BarricadeBlock.Part.SINGLE:(i==0?BarricadeBlock.Part.BOTTOM:BarricadeBlock.Part.TOP);
   states[i]=Barricades.BARRICADE.getDefaultState().with(BarricadeBlock.FACING,plan.side())
    .with(BarricadeBlock.PART,part).with(BarricadeBlock.VARIANT,plan.variant());
  }
  for(int i=0;i<states.length;i++)if(!states[i].canPlaceAt(world,cells.get(i)))return ActionResult.FAIL;
  for(int i=0;i<states.length;i++)world.setBlockState(cells.get(i),states[i],Block.NOTIFY_ALL);
  BlockPos first=cells.get(0);
  world.playSound(player,first,states[0].getSoundGroup().getPlaceSound(),SoundCategory.BLOCKS,1.0f,.85f);
  world.emitGameEvent(GameEvent.BLOCK_PLACE,first,GameEvent.Emitter.of(player,states[0]));
  if(!creative)ctx.getStack().decrement(states.length);
  return ActionResult.success(world.isClient);
 }
}
