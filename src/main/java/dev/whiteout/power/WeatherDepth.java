package dev.whiteout.power;

import dev.whiteout.client.OpaqueDepthSelector;
import dev.whiteout.client.WinterEffect;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Vanilla needs a depth snapshot BEFORE glass, just as Iris supplies depthtex1. */
public final class WeatherDepth {
 private static SimpleFramebuffer opaque;
 private static boolean captured;
 private WeatherDepth(){}
 public static void register(){
  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc->{
   if(opaque!=null){opaque.delete();opaque=null;}captured=false;
  });
  WorldRenderEvents.START.register(ctx->captured=false);
 }
 /** Called after entity buffers flush, immediately before translucent terrain.
  * BEFORE_DEBUG_RENDER is too early: player skins and custom model layers can
  * still be queued there, leaving background depth under foreground objects. */
 public static void captureBeforeTranslucent(){
   if(captured||WinterEffect.shaderPack())return; // Iris supplies its own depthtex1.
   MinecraftClient mc=MinecraftClient.getInstance();
   var source=mc.getFramebuffer();
   if(source.textureWidth<=0||source.textureHeight<=0||source.getDepthAttachment()<0)return;
   int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
   int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
   int texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
   try{
    if(opaque==null)opaque=new SimpleFramebuffer(source.textureWidth,source.textureHeight,true,MinecraftClient.IS_SYSTEM_MAC);
    else if(opaque.textureWidth!=source.textureWidth||opaque.textureHeight!=source.textureHeight)
     opaque.resize(source.textureWidth,source.textureHeight,MinecraftClient.IS_SYSTEM_MAC);
    opaque.copyDepthFrom(source);
    captured=true;
   }finally{
    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
    com.mojang.blaze3d.systems.RenderSystem.bindTexture(texture);
   }
 }
 public static int select(int raw){
  if(!WinterEffect.shaderPack()&&captured&&opaque!=null)return opaque.getDepthAttachment();
  return OpaqueDepthSelector.select(raw);
 }
}

