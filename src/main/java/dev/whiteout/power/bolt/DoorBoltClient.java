package dev.whiteout.power.bolt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Client side: receives nearby bolts and draws them on the (possibly opened) door leaf. */
public final class DoorBoltClient implements ClientModInitializer {
 private static final Identifier TEXTURE=Identifier.of("whiteout","textures/entity/door_bolt.png");
 // Regions of the 16x16 texture: {u0,v0,u1,v1}
 private static final float[] PLATE={0f,0f,.5f,.5f},BAR={.5f,0f,1f,.5f},BRACKET={0f,.5f,.5f,1f},KNOB={.5f,.5f,1f,1f};
 private static final long SLIDE_MS=160;
 private static BufferAllocator buffer;

 @Override public void onInitializeClient(){
  ClientPlayNetworking.registerGlobalReceiver(DoorBoltSync.ID,(p,c)->c.client().execute(()->ClientCache.apply(p.dimension(),p.lines())));
  ClientPlayConnectionEvents.DISCONNECT.register((h,client)->ClientCache.clear());
  WorldRenderEvents.AFTER_TRANSLUCENT.register(DoorBoltClient::render);
 }

 private static void render(WorldRenderContext ctx){
  if(ClientCache.MAP.isEmpty()||ctx.world()==null||ctx.matrixStack()==null)return;
  if(!ctx.world().getRegistryKey().getValue().toString().equals(ClientCache.dimension))return;
  Vec3d cam=ctx.camera().getPos();MatrixStack matrices=ctx.matrixStack();
  if(buffer==null)buffer=new BufferAllocator(1<<16);
  VertexConsumerProvider.Immediate batch=VertexConsumerProvider.immediate(buffer);
  VertexConsumer out=batch.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
  matrices.push();matrices.translate(-cam.x,-cam.y,-cam.z);
  MatrixStack.Entry m=matrices.peek();long now=System.currentTimeMillis();boolean any=false;
  for(var e:ClientCache.MAP.entrySet()){
   BlockPos low=BlockPos.fromLong(e.getKey());
   if(low.getSquaredDistance(cam)>40*40||!ctx.world().isChunkLoaded(low))continue;
   BlockState s=ctx.world().getBlockState(low);
   if(!(s.getBlock() instanceof DoorBlock)||!s.contains(Properties.DOOR_HINGE)||s.get(Properties.DOUBLE_BLOCK_HALF)!=DoubleBlockHalf.LOWER)continue;
   if(ctx.frustum()!=null&&!ctx.frustum().isVisible(new Box(low).stretch(0,1,0).expand(.3)))continue;
   ClientCache.Entry b=e.getValue();
   float t=b.changedAt()==0?1f:Math.min(1f,(now-b.changedAt())/(float)SLIDE_MS);
   float target=b.locked()&&!s.get(Properties.OPEN)?1f:0f;
   float shot=target==1f?t:1f-t; // slide animation toward the synced state
   if(s.get(Properties.OPEN))shot=0f; // never draw a shot bolt on an opened door
   int light=WorldRenderer.getLightmapCoordinates(ctx.world(),low.up());
   draw(out,m,light,low,s,b.inner(),shot);any=true;
  }
  matrices.pop();
  if(any)batch.draw();
 }

 private static void draw(VertexConsumer out,MatrixStack.Entry m,int light,BlockPos low,BlockState s,boolean inner,float shot){
  DoorLeaf.Frame f=DoorLeaf.frame(s);double h=DoorLeaf.BOLT_HEIGHT*16;
  Ctx c=new Ctx(out,m,light,low,f,inner);
  c.box(6,15,h-1.5,h+1.5,0,.6,PLATE);                         // base plate
  c.box(7,9,h-1.5,h+1.5,.6,2.0,BRACKET);                      // guide A
  c.box(12.5,14.5,h-1.5,h+1.5,.6,2.0,BRACKET);                // guide B
  double s0=4.5+3.0*shot;                                     // 4.5 retracted -> 7.5 shot
  c.box(s0,s0+9,h-.6,h+.6,2.0,3.2,BAR);                       // sliding bar (tip passes the free edge)
  c.box(s0+2.2,s0+3.6,h+.6,h+2.4,2.0,3.2,KNOB);              // handle
 }

 private record Ctx(VertexConsumer out,MatrixStack.Entry m,int light,BlockPos low,DoorLeaf.Frame f,boolean inner){
  void box(double s0,double s1,double h0,double h1,double d0,double d1,float[] uv){
   double[][] c=new double[8][];
   for(int i=0;i<8;i++)c[i]=f.point(low,inner,(i&1)==0?s0:s1,(i&2)==0?h0:h1,(i&4)==0?d0:d1);
   float wx=f.w().getOffsetX(),wz=f.w().getOffsetZ(),nx=f.mountNormal(inner).getOffsetX(),nz=f.mountNormal(inner).getOffsetZ();
   quad(c[0],c[2],c[6],c[4],-wx,0,-wz,uv);quad(c[1],c[3],c[7],c[5],wx,0,wz,uv);       // s faces
   quad(c[0],c[1],c[5],c[4],0,-1,0,uv);quad(c[2],c[3],c[7],c[6],0,1,0,uv);            // h faces
   quad(c[0],c[1],c[3],c[2],-nx,0,-nz,uv);quad(c[4],c[5],c[7],c[6],nx,0,nz,uv);       // d faces
  }
  void quad(double[] a,double[] b,double[] c,double[] d,float nx,float ny,float nz,float[] uv){
   v(a,uv[0],uv[3],nx,ny,nz);v(b,uv[0],uv[1],nx,ny,nz);v(c,uv[2],uv[1],nx,ny,nz);v(d,uv[2],uv[3],nx,ny,nz);
  }
  void v(double[] p,float u,float vv,float nx,float ny,float nz){
   out.vertex(m.getPositionMatrix(),(float)p[0],(float)p[1],(float)p[2]).color(255,255,255,255)
    .texture(u,vv).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m,nx,ny,nz);
  }
 }
}
