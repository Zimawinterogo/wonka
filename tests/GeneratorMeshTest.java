import dev.whiteout.power.GeneratorMesh;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
public final class GeneratorMeshTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void rejects(byte[] data)throws Exception {
  try{GeneratorMesh.read(new ByteArrayInputStream(data));throw new AssertionError("Accepted invalid mesh");}
  catch(IOException expected){}
 }
 public static void main(String[] args)throws Exception {
  String[] files={"generator","old_generator","generator_far","old_generator_far"};
  int[] counts={6560,6818,1472,1730};
  for(int k=0;k<files.length;k++){
   byte[] bytes=Files.readAllBytes(Path.of("src/main/resources/assets/whiteout/meshes/"+files[k]+".mesh"));
   var mesh=GeneratorMesh.read(new ByteArrayInputStream(bytes));
   check(mesh.quads()==counts[k],"Actual v7 geometry count");int lamps=0;
   for(int q=0;q<mesh.quads();q++){
    if(mesh.lamp(q))lamps++;
    int i=q*32;double[] a=new double[3],b=new double[3],normal=new double[3];
    for(int j=0;j<3;j++){a[j]=mesh.value(i+8+j)-mesh.value(i+j);b[j]=mesh.value(i+16+j)-mesh.value(i+j);normal[j]=mesh.value(i+5+j);}
    double dot=(a[1]*b[2]-a[2]*b[1])*normal[0]+(a[2]*b[0]-a[0]*b[2])*normal[1]+(a[0]*b[1]-a[1]*b[0])*normal[2];
    check(dot>0,"Outward winding matches smooth normals: "+files[k]+" quad "+q);
   }
   check(lamps==6,"Only existing lamp has six animated faces");
   rejects(Arrays.copyOf(bytes,bytes.length-1));
   rejects(Arrays.copyOf(bytes,bytes.length+1));
   byte[] invalid=bytes.clone();invalid[0]=0;rejects(invalid);
   invalid=bytes.clone();Arrays.fill(invalid,4,8,(byte)127);rejects(invalid);
   invalid=bytes.clone();invalid[9]=0x7f;invalid[10]=(byte)0xc0;invalid[11]=invalid[12]=0;rejects(invalid);
  }
  System.out.println("PASS GeneratorMeshTest: real v7/LOD assets, winding, normals, lamp mapping and malformed input");
 }
}
