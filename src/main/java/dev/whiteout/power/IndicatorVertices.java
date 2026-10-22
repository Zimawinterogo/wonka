package dev.whiteout.power;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;

/** Only dims existing lime indicator faces. No new geometry, texture or light source. */
final class IndicatorVertices implements VertexConsumer {
 private final VertexConsumer out;
 private final float brightness;
 IndicatorVertices(VertexConsumer out,float brightness){this.out=out;this.brightness=brightness;}
 private float gain(BakedQuad quad){return quad.getSprite().getContents().getId().toString().equals("minecraft:block/lime_concrete")?brightness:1;}
 @Override public void quad(MatrixStack.Entry entry,BakedQuad quad,float r,float g,float b,float a,int light,int overlay){
  float f=gain(quad);out.quad(entry,quad,r*f,g*f,b*f,a,light,overlay);
 }
 @Override public void quad(MatrixStack.Entry entry,BakedQuad quad,float[] shades,float r,float g,float b,float a,int[] lights,int overlay,boolean color){
  float f=gain(quad);out.quad(entry,quad,shades,r*f,g*f,b*f,a,lights,overlay,color);
 }
 public VertexConsumer vertex(float x,float y,float z){out.vertex(x,y,z);return this;}
 public VertexConsumer color(int r,int g,int b,int a){out.color(r,g,b,a);return this;}
 public VertexConsumer texture(float u,float v){out.texture(u,v);return this;}
 public VertexConsumer overlay(int u,int v){out.overlay(u,v);return this;}
 public VertexConsumer light(int u,int v){out.light(u,v);return this;}
 public VertexConsumer normal(float x,float y,float z){out.normal(x,y,z);return this;}
}
