package dev.whiteout.power.barricade;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/** Cosmetic connected models: no saved properties, block entities or changes to game rules. */
public final class BarricadeModels {
 private BarricadeModels(){}
 private static Identifier id(int phase,int mask){
  return Identifier.of("whiteout","block/barricade_connected_"+phase+"_"+mask);
 }
 public static void register(){
  ModelLoadingPlugin.register(plugin->{
   for(int phase=0;phase<2;phase++)for(int mask=0;mask<16;mask++)plugin.addModels(id(phase,mask));
   // Cache belongs to this reload, not the world; never retains stale atlas sprites.
   BakedModel[][][] connected=new BakedModel[4][2][16];
   plugin.modifyModelAfterBake().register((original,ctx)->{
    var top=ctx.topLevelId();
    if(original==null||top==null||!top.id().equals(Identifier.of("whiteout","barricade")))return original;
    // Vanilla's blockstate rotation is already encoded in the fallback model.
    Direction facing=null;
    for(Direction d:Direction.Type.HORIZONTAL)
     if(top.variant().contains("facing="+d.asString()))facing=d;
    if(facing==null)return original;
    int fi=facing.getHorizontal();
    int degrees=switch(facing){case WEST->90;case NORTH->180;case EAST->270;default->0;};
    for(int phase=0;phase<2;phase++)for(int mask=0;mask<16;mask++)
     if(connected[fi][phase][mask]==null)
      connected[fi][phase][mask]=ctx.baker().bake(id(phase,mask),ModelRotation.get(0,degrees));
    return new Connected(original,connected[fi]);
   });
  });
 }
 private static final class Connected extends ForwardingBakedModel {
  private final BakedModel[][] models;
  Connected(BakedModel original,BakedModel[][] models){wrapped=original;this.models=models;}
  @Override public boolean isVanillaAdapter(){return false;}
  @Override public void emitBlockQuads(BlockRenderView world,BlockState state,BlockPos pos,
    Supplier<Random> random,RenderContext context){
   int mask=BarricadeConnections.mask(world,state,pos);
   models[Math.floorMod(pos.getY(),2)][mask].emitBlockQuads(world,state,pos,random,context);
  }
 }
}
