package dev.whiteout.power;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.LoggerFactory;

/** Shared mesh renderer for placed blocks, inventory, held and dropped items. */
public final class GeneratorModels {
 private static final Identifier[] TEXTURES={PowerDevices.id("textures/block/new_generator_atlas.png"),PowerDevices.id("textures/block/old_generator_atlas.png")};
 private static final RenderLayer[] LAYERS={RenderLayer.getEntityCutout(TEXTURES[0]),RenderLayer.getEntityCutout(TEXTURES[1])};
 private static volatile GeneratorMesh[] meshes=new GeneratorMesh[4];
 private GeneratorModels(){}
 public static void register(){
  ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener(){
   public Identifier getFabricId(){return PowerDevices.id("generator_meshes");}
   public void reload(ResourceManager manager){
    GeneratorMesh[] next=new GeneratorMesh[4];String[] names={"generator","old_generator"};
    for(int kind=0;kind<2;kind++)for(int lod=0;lod<2;lod++){
     Identifier id=PowerDevices.id("meshes/"+names[kind]+(lod==1?"_far":"")+".mesh");
     try(var in=manager.getResourceOrThrow(id).getInputStream()){next[kind+lod*2]=GeneratorMesh.read(in);}
     catch(Exception ex){LoggerFactory.getLogger("Whiteout").error("Could not load generator model {}; using baked fallback",id,ex);}
    }
    meshes=next;
   }
  });
  BuiltinItemRendererRegistry.DynamicItemRenderer item=(stack,mode,matrices,vertices,light,overlay)->
   render(stack.isOf(PowerDevices.OLD_GENERATOR.asItem()),false,false,1,Direction.NORTH,matrices,vertices,light,overlay);
  BuiltinItemRendererRegistry.INSTANCE.register(PowerDevices.GENERATOR,item);
  BuiltinItemRendererRegistry.INSTANCE.register(PowerDevices.OLD_GENERATOR,item);
 }
 public static void render(boolean worn,boolean active,boolean far,float indicator,Direction facing,MatrixStack matrices,VertexConsumerProvider vertices,int light,int overlay){
  GeneratorMesh[] available=meshes;int kind=worn?1:0;GeneratorMesh mesh=available[kind+(far?2:0)];
  if(mesh==null)mesh=available[kind];
  if(mesh==null){
   var state=(worn?PowerDevices.OLD_GENERATOR:PowerDevices.GENERATOR).getDefaultState().with(DeviceBlock.FACING,facing).with(DeviceBlock.ACTIVE,active);
   var manager=MinecraftClient.getInstance().getBlockRenderManager();
   manager.getModelRenderer().render(matrices.peek(),vertices.getBuffer(RenderLayers.getEntityBlockLayer(state,false)),state,manager.getModel(state),1,1,1,light,overlay);
   return;
  }
  matrices.push();matrices.translate(.5,.5,.5);
  matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(switch(facing){case EAST->-90;case SOUTH->180;case WEST->90;default->0;}));
  matrices.translate(-.5,-.5,-.5);
  var entry=matrices.peek();var buffer=vertices.getBuffer(LAYERS[kind]);
  for(int q=0;q<mesh.quads();q++){
   float tint=mesh.lamp(q)?(active?indicator:.3f):1;
   for(int v=0;v<4;v++){
    int i=q*32+v*8;
    buffer.vertex(entry,mesh.value(i),mesh.value(i+1),mesh.value(i+2)).color(tint,tint,tint,1)
     .texture(mesh.value(i+3),mesh.value(i+4)).overlay(overlay).light(light)
     .normal(entry,mesh.value(i+5),mesh.value(i+6),mesh.value(i+7));
   }
  }
  matrices.pop();
 }
}
