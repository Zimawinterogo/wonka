package dev.whiteout.power;

import java.io.*;

/** Compact immutable render data. Parsed on resource reload, never per frame. */
public final class GeneratorMesh {
 private final float[] vertices;
 private final boolean[] lamps;
 private GeneratorMesh(float[] vertices,boolean[] lamps){this.vertices=vertices;this.lamps=lamps;}
 public int quads(){return lamps.length;}
 public boolean lamp(int quad){return lamps[quad];}
 public float value(int index){return vertices[index];}
 public static GeneratorMesh read(InputStream input)throws IOException {
  DataInputStream in=new DataInputStream(input);
  if(in.readInt()!=0x57474D31)throw new IOException("Invalid generator mesh header");
  int count=in.readInt();if(count<1||count>10000)throw new IOException("Invalid generator mesh size");
  float[] vertices=new float[count*32];boolean[] lamps=new boolean[count];
  for(int q=0;q<count;q++){
   lamps[q]=in.readBoolean();
   for(int v=0;v<4;v++){
    int offset=q*32+v*8;
    for(int k=0;k<8;k++){
     float n=in.readFloat();
     if(!Float.isFinite(n)||(k<5&&(n<0||n>1))||(k>=5&&Math.abs(n)>1.001f))throw new IOException("Invalid generator vertex");
     vertices[offset+k]=n;
    }
    double length=0;for(int k=5;k<8;k++)length+=vertices[offset+k]*vertices[offset+k];
    if(Math.abs(length-1)>.01)throw new IOException("Invalid generator normal");
   }
  }
  if(in.read()!=-1)throw new IOException("Unexpected generator mesh data");
  return new GeneratorMesh(vertices,lamps);
 }
}
