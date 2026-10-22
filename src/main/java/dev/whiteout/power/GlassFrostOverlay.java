package dev.whiteout.power;

import dev.whiteout.Zone;
import dev.whiteout.client.WeatherState;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Visual frost on vanilla glass (never replaces world blocks or changes NBT).
 * The cache belongs to the client world and persists when the player walks away.
 * A bounded cache avoids retaining every glass block visited in a large world.
 */
public final class GlassFrostOverlay {
 private static final int SCAN_RADIUS=20, SCAN_HEIGHT=8;
 private static final int MAX_WINDOWS=2048, TILES=8;
 private static final RenderLayer[] FROST_LAYERS = new RenderLayer[4];
 static {
  for(int stage=1;stage<=3;stage++)FROST_LAYERS[stage]=RenderLayer.getEntityTranslucent(
   Identifier.of("whiteout","textures/block/frost_window_"+stage+".png"));
 }
 private static final class Cell {
  int age, recoveryCounter;
  BlockPos exterior;
  int nextExposure;
  long clearedTiles;
  int stage(){return age>=55?3:age>=32?2:age>=12?1:0;}
 }
 private static final Map<BlockPos,Cell> CELLS=new LinkedHashMap<>();
 private static ClientWorld cachedWorld;
 private static boolean enabled=true;
 private static int ticks;
 private static boolean wipingHeld;
 private static int lastWipeFxTick=-1000;
 private static BufferAllocator vertexBuffer;
 private GlassFrostOverlay(){}

 public static void register(){
  ClientTickEvents.END_CLIENT_TICK.register(GlassFrostOverlay::tick);
  WorldRenderEvents.AFTER_TRANSLUCENT.register(GlassFrostOverlay::render);
  HudRenderCallback.EVENT.register((context,delta)->hint(context));
  UseBlockCallback.EVENT.register((player,world,hand,hit)->{
   if(!world.isClient||hand!=Hand.MAIN_HAND||!canWipe())return ActionResult.PASS;
   if(MinecraftClient.getInstance().world!=world)return ActionResult.PASS;
   return ActionResult.CONSUME;
  });
 }
 public static void setEnabled(boolean value){enabled=value;if(!value){CELLS.clear();wipingHeld=false;}}
 private static void reset(){CELLS.clear();cachedWorld=null;ticks=0;enabled=true;wipingHeld=false;lastWipeFxTick=-1000;}

 private static boolean glass(BlockState state){
  if(state.isAir())return false; // Most blocks around a player are air; avoid registry calls.
  Identifier id=Registries.BLOCK.getId(state.getBlock());
  if(!"minecraft".equals(id.getNamespace()))return false;
  String path=id.getPath();
  return path.equals("glass")||path.equals("glass_pane")||path.equals("tinted_glass")
   ||path.endsWith("_stained_glass")||path.endsWith("_stained_glass_pane");
 }
 /** Geometry is cached briefly, but weather is read from the current snapshot every update. */
 private static boolean outsideBlizzard(ClientWorld world,BlockPos at,Cell cell,String dimension){
  if(cell.exterior==null)return false;
  Zone zone=WindowExposure.weather(cell.exterior,dimension);
  return zone!=null&&zone.blizzard;
 }
 private static void tick(MinecraftClient client){
  ClientWorld world=client.world;
  if(world==null||client.player==null){reset();return;}
  if(cachedWorld!=world){CELLS.clear();cachedWorld=world;ticks=0;wipingHeld=false;lastWipeFxTick=-1000;}
  if(!client.mouse.isCursorLocked()||client.currentScreen!=null)wipingHeld=false;
  if(!enabled||client.isPaused())return;
  ticks++;
  // An uninterrupted drag wipes every tick. This is not gated by the vanilla
  // four-tick item use delay, and works with remapped keyboard/mouse use keys.
  if(wipingHeld&&canWipe())wipe((BlockHitResult)client.crosshairTarget,Hand.MAIN_HAND,false);
  // Spread expensive connectivity probes across ticks instead of a once-per-second spike.
  int exposureBudget=8;
  for(var entry:CELLS.entrySet()){
   Cell cell=entry.getValue();BlockPos at=entry.getKey();
   if(ticks>=cell.nextExposure&&world.isChunkLoaded(at)){
    cell.exterior=WindowExposure.find(world,at);cell.nextExposure=ticks+Math.max(100,(CELLS.size()+7)/8+1);
    if(--exposureBudget==0)break;
   }
  }
  // Discover one fifth of the vertical scan volume every four ticks. The
  // original all-at-once scan caused a large periodic client-thread spike,
  // even though the total number of probes per second was unchanged.
  if(ticks%4==0){
   BlockPos origin=client.player.getBlockPos();
   int slice=(ticks/4-1)%5;
   BlockPos.Mutable at=new BlockPos.Mutable();
   for(int y=-SCAN_HEIGHT;y<=SCAN_HEIGHT;y++){
    if((y+SCAN_HEIGHT)%5!=slice)continue;
    for(int x=-SCAN_RADIUS;x<=SCAN_RADIUS;x++)
     for(int z=-SCAN_RADIUS;z<=SCAN_RADIUS;z++){
      if(x*x+z*z>SCAN_RADIUS*SCAN_RADIUS)continue;
      at.set(origin.getX()+x,origin.getY()+y,origin.getZ()+z);
      if(!world.isInBuildLimit(at)||!world.isChunkLoaded(at))continue;
      BlockState state=world.getBlockState(at);
      Cell cell=CELLS.get(at);
      if(!glass(state)){
       if(cell!=null)CELLS.remove(at); // broken/replaced glass
       continue;
      }
      if(cell==null){
       if(CELLS.size()>=MAX_WINDOWS)continue;
       cell=new Cell();CELLS.put(at.toImmutable(),cell);
      }
     }
   }
  }
  if(ticks%20!=0)return;
  String dimension=world.getRegistryKey().getValue().toString();
  if(CELLS.size()>=MAX_WINDOWS)CELLS.entrySet().removeIf(e->e.getValue().age==0||!world.isChunkLoaded(e.getKey()));
  // Cache lifetime is independent of detection distance. Never delete cells
  // just because the player moved more than SCAN_RADIUS blocks away.
  // Update every retained window, independently of the discovery radius.
  Iterator<Map.Entry<BlockPos,Cell>> updates=CELLS.entrySet().iterator();
  while(updates.hasNext()){
   var entry=updates.next();BlockPos at=entry.getKey();Cell cell=entry.getValue();
   if(world.isChunkLoaded(at)&&!glass(world.getBlockState(at))){updates.remove();continue;}
   boolean storm=outsideBlizzard(world,at,cell,dimension);
   cell.age=FrostProgress.advance(cell.age,storm);
   if(storm){
    if(cell.clearedTiles!=0&&++cell.recoveryCounter>=6){
     cell.recoveryCounter=0;cell.clearedTiles&=cell.clearedTiles-1;
    }
   }else cell.recoveryCounter=0;
   if(cell.age==0)cell.clearedTiles=0;
  }

 }
 private static void hint(DrawContext context){
  MinecraftClient mc=MinecraftClient.getInstance();
  if(!enabled||mc.player==null||mc.world!=cachedWorld||mc.currentScreen!=null||mc.options.hudHidden)return;
  if(!(mc.crosshairTarget instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK)return;
  Cell cell=CELLS.get(hit.getBlockPos());
  if(cell==null||cell.stage()==0||cell.clearedTiles==-1L||!glass(mc.world.getBlockState(hit.getBlockPos())))return;
  Text text=Text.translatable("hint.whiteout.wipe_glass");
  context.drawCenteredTextWithShadow(mc.textRenderer,text,context.getScaledWindowWidth()/2,
   context.getScaledWindowHeight()/2+22,0xDDEAF3);
 }
 /** A physical use press starts a drag independent of vanilla item cooldowns. */
 public static boolean canWipe(){
  MinecraftClient mc=MinecraftClient.getInstance();
  if(!enabled||mc.player==null||mc.world!=cachedWorld||mc.currentScreen!=null
    ||!mc.player.getMainHandStack().isEmpty()||!mc.player.getOffHandStack().isEmpty())return false;
  if(!(mc.crosshairTarget instanceof BlockHitResult hit)||hit.getSide().getAxis()==Direction.Axis.Y)return false;
  Cell cell=CELLS.get(hit.getBlockPos());
  return cell!=null&&cell.stage()>0&&cell.clearedTiles!=-1L&&glass(mc.world.getBlockState(hit.getBlockPos()));
 }
 public static boolean press(){
  if(!canWipe())return false;
  wipingHeld=true;
  wipe((BlockHitResult)MinecraftClient.getInstance().crosshairTarget,Hand.MAIN_HAND,true);
  return true;
 }
 public static void release(){wipingHeld=false;}
 /** Only visual grid tiles are removed; the original block is never touched. */
 private static boolean wipe(BlockHitResult hit,Hand hand,boolean physicalPress){
  MinecraftClient mc=MinecraftClient.getInstance();
  if(mc.player==null||mc.world==null||hit.getType()!=HitResult.Type.BLOCK)return false;
  BlockPos pos=hit.getBlockPos();
  Cell cell=CELLS.get(pos);
  if(cell==null||cell.stage()==0||!glass(mc.world.getBlockState(pos)))return false;
  Direction face=hit.getSide();
  if(face.getAxis()==Direction.Axis.Y)return false;
  BlockState state=mc.world.getBlockState(pos);
  Vec3d local=hit.getPos().subtract(pos.getX(),pos.getY(),pos.getZ());
  float horizontal=(float)(face.getAxis()==Direction.Axis.X?local.z:local.x);
  if(state.getBlock() instanceof PaneBlock){
   float start=.0f,end=1f;
   if(face.getAxis()==Direction.Axis.X){
    start=state.get(PaneBlock.NORTH)?0f:.4375f;
    end=state.get(PaneBlock.SOUTH)?1f:.5625f;
   }else{
    start=state.get(PaneBlock.WEST)?0f:.4375f;
    end=state.get(PaneBlock.EAST)?1f:.5625f;
   }
   horizontal=(horizontal-start)/Math.max(.01f,end-start);
  }
  float u=MathHelper.clamp(horizontal,0f,1f);
  float v=1f-MathHelper.clamp((float)local.y,0f,1f);
  long newlyWiped=0;
  for(int row=0;row<TILES;row++)for(int col=0;col<TILES;col++){
   float du=(col+.5f)/TILES-u,dv=(row+.5f)/TILES-v;
   if(du*du+dv*dv<=.22f*.22f)newlyWiped|=1L<<(row*TILES+col);
  }
  if(newlyWiped==0){
   int col=Math.min(TILES-1,(int)(u*TILES));
   int row=Math.min(TILES-1,(int)(v*TILES));
   newlyWiped=1L<<(row*TILES+col);
  }
  if((cell.clearedTiles&newlyWiped)==newlyWiped){
   // Repeated clicks on one spot still remove adjacent remaining frost;
   // previously they silently did nothing, which felt like a cooldown.
   int nearest=-1;float best=Float.MAX_VALUE;
   for(int row=0;row<TILES;row++)for(int col=0;col<TILES;col++){
    int index=row*TILES+col;
    if((cell.clearedTiles&(1L<<index))!=0)continue;
    float du=(col+.5f)/TILES-u,dv=(row+.5f)/TILES-v;
    float distance=du*du+dv*dv;
    if(distance<best){best=distance;nearest=index;}
   }
   if(nearest<0)return false;
   newlyWiped=1L<<nearest;
  }
  cell.clearedTiles|=newlyWiped;
  cell.recoveryCounter=0;
  // Limit only repetitive audiovisual effects, never removal of frost tiles.
  if(physicalPress||ticks-lastWipeFxTick>=4){
   lastWipeFxTick=ticks;
   mc.player.swingHand(hand);
   mc.world.playSound(mc.player,pos,SoundEvents.BLOCK_WOOL_HIT,SoundCategory.BLOCKS,.45f,1.15f);
  }
  return true;
 }
 private static void render(WorldRenderContext ctx){
  if(!enabled||CELLS.isEmpty()||ctx.world()!=cachedWorld||ctx.matrixStack()==null)return;
  Vec3d camera=ctx.camera().getPos();
  MatrixStack matrices=ctx.matrixStack();
  if(vertexBuffer==null)vertexBuffer=new BufferAllocator(1<<18);
  VertexConsumerProvider.Immediate batch=VertexConsumerProvider.immediate(vertexBuffer);
  matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);
  MatrixStack.Entry transform=matrices.peek();
  for(Map.Entry<BlockPos,Cell> entry:CELLS.entrySet()){
   Cell cell=entry.getValue();int stage=cell.stage();
   if(stage==0||cell.clearedTiles==-1L)continue;
   BlockPos pos=entry.getKey();
   if(!ctx.world().isChunkLoaded(pos))continue;
   // Account for quads offset beyond block bounds, while only skipping
   // genuinely off-screen glass; never use a separate distance threshold.
   if(ctx.frustum()!=null&&!ctx.frustum().isVisible(new net.minecraft.util.math.Box(pos).expand(.15)))continue;
   BlockState state=ctx.world().getBlockState(pos);
   if(!glass(state))continue;
   double dx=camera.x-(pos.getX()+.5), dz=camera.z-(pos.getZ()+.5);
   // Keep opacity stable across viewing distances. Loaded glass should not
   // vanish because a client-side radius/fade threshold was crossed.
   int alpha=190;
   VertexConsumer consumer=batch.getBuffer(FROST_LAYERS[stage]);
   int light=WorldRenderer.getLightmapCoordinates(ctx.world(),pos);
   if(state.getBlock() instanceof PaneBlock){
    boolean ns=state.get(PaneBlock.NORTH)||state.get(PaneBlock.SOUTH);
    boolean ew=state.get(PaneBlock.EAST)||state.get(PaneBlock.WEST);
    if(ns||!ew){
     float x=(float)(pos.getX()+.5+(dx>=0?.067:-.067));
     float start=state.get(PaneBlock.NORTH)?0f:.4375f;
     float end=state.get(PaneBlock.SOUTH)?1f:.5625f;
     paneX(consumer,transform,x,pos.getY(),pos.getZ()+start,pos.getZ()+end,light,alpha,cell.clearedTiles);
    }
    if(ew||!ns){
     float z=(float)(pos.getZ()+.5+(dz>=0?.067:-.067));
     float start=state.get(PaneBlock.WEST)?0f:.4375f;
     float end=state.get(PaneBlock.EAST)?1f:.5625f;
     paneZ(consumer,transform,z,pos.getY(),pos.getX()+start,pos.getX()+end,light,alpha,cell.clearedTiles);
    }
   }else {
    if(Math.abs(dx)>.5){
    paneX(consumer,transform,pos.getX()+(dx>0?1.003f:-.003f),pos.getY(),
     pos.getZ(),pos.getZ()+1f,light,alpha,cell.clearedTiles);
    }
    if(Math.abs(dz)>.5){
    paneZ(consumer,transform,pos.getZ()+(dz>0?1.003f:-.003f),pos.getY(),
     pos.getX(),pos.getX()+1f,light,alpha,cell.clearedTiles);
    }
   }
  }
  matrices.pop();batch.draw();
 }
 private static void vertex(VertexConsumer out,MatrixStack.Entry transform,float x,float y,float z,
                            float u,float v,int light,int alpha,float nx,float nz){
  out.vertex(transform.getPositionMatrix(),x,y,z).color(255,255,255,alpha)
   .texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(transform,nx,0,nz);
 }
 private static void paneX(VertexConsumer out,MatrixStack.Entry m,float x,float y,float z0,float z1,
                           int light,int alpha,long cleared){
  if(cleared==0){
   vertex(out,m,x,y,z0,0,1,light,alpha,1,0);
   vertex(out,m,x,y+1,z0,0,0,light,alpha,1,0);
   vertex(out,m,x,y+1,z1,1,0,light,alpha,1,0);
   vertex(out,m,x,y,z1,1,1,light,alpha,1,0);
   return;
  }
  for(int row=0;row<TILES;row++)for(int col=0;col<TILES;col++){
   if((cleared&(1L<<(row*TILES+col)))!=0)continue;
   float u0=(float)col/TILES,u1=(float)(col+1)/TILES;
   float v0=(float)row/TILES,v1=(float)(row+1)/TILES;
   float a=z0+(z1-z0)*u0,b=z0+(z1-z0)*u1;
   vertex(out,m,x,y+1-v1,a,u0,v1,light,alpha,1,0);
   vertex(out,m,x,y+1-v0,a,u0,v0,light,alpha,1,0);
   vertex(out,m,x,y+1-v0,b,u1,v0,light,alpha,1,0);
   vertex(out,m,x,y+1-v1,b,u1,v1,light,alpha,1,0);
  }
 }
 private static void paneZ(VertexConsumer out,MatrixStack.Entry m,float z,float y,float x0,float x1,
                           int light,int alpha,long cleared){
  if(cleared==0){
   vertex(out,m,x0,y,z,0,1,light,alpha,0,1);
   vertex(out,m,x0,y+1,z,0,0,light,alpha,0,1);
   vertex(out,m,x1,y+1,z,1,0,light,alpha,0,1);
   vertex(out,m,x1,y,z,1,1,light,alpha,0,1);
   return;
  }
  for(int row=0;row<TILES;row++)for(int col=0;col<TILES;col++){
   if((cleared&(1L<<(row*TILES+col)))!=0)continue;
   float u0=(float)col/TILES,u1=(float)(col+1)/TILES;
   float v0=(float)row/TILES,v1=(float)(row+1)/TILES;
   float a=x0+(x1-x0)*u0,b=x0+(x1-x0)*u1;
   vertex(out,m,a,y+1-v1,z,u0,v1,light,alpha,0,1);
   vertex(out,m,a,y+1-v0,z,u0,v0,light,alpha,0,1);
   vertex(out,m,b,y+1-v0,z,u1,v0,light,alpha,0,1);
   vertex(out,m,b,y+1-v1,z,u1,v1,light,alpha,0,1);
  }
 }
}
