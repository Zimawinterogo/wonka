import dev.whiteout.power.backpack.BackpackLayout;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Asset/layout preview only, not a Minecraft screenshot or interaction test.
 * java --class-path build/classes/java/main tools/PreviewBackpack.java
 */
public class PreviewBackpack {
 public static void main(String[] args)throws Exception{
  Path assets=Path.of("src/main/resources/assets/whiteout");
  BufferedImage image=new BufferedImage(1280,960,BufferedImage.TYPE_INT_ARGB);
  Graphics2D g=image.createGraphics();g.scale(4,4);g.setColor(new Color(13,21,28));g.fillRect(0,0,320,240);
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
  int top=8;g.drawImage(ImageIO.read(assets.resolve("textures/gui/backpack/bag.png").toFile()),0,top,320,224,null);
  g.setColor(new Color(3,11,16,64));g.fillRect(36,top+61,186,89);g.fillRect(244,top+55,35,144);
  for(int id=5;id<46;id++){
   var at=BackpackLayout.position(id);int sx=at.x()-2,sy=top+at.y()-2;
   g.setColor(new Color(id<9||id==45?0x9BA9AF:0x72868F));g.fillRect(sx,sy,20,20);
   g.setColor(new Color(0x273841));g.fillRect(sx+1,sy+1,18,18);
   g.setColor(new Color(0x0B151D));g.fillRect(sx+2,sy+2,16,16);
  }
  g.setFont(new Font("Monospaced",Font.PLAIN,9));g.setColor(new Color(0xD8E4E7));
  g.setColor(new Color(0x161E23));g.fillRect(121,top+17,78,19);
  g.setColor(new Color(0x3C484C));g.fillRect(123,top+19,74,15);
  g.setColor(new Color(0xACAD99));for(int stitch=125;stitch<196;stitch+=5){g.fillRect(stitch,top+19,2,1);g.fillRect(stitch,top+33,2,1);}
  g.setColor(new Color(0xE0E7E6));centered(g,"BACKPACK",160,top+23);
  g.setColor(new Color(0xB4C5CA));g.drawString("СОДЕРЖИМОЕ",40,top+49+8);
  g.setColor(new Color(0x94A7AE));g.drawString("БЫСТРЫЙ ДОСТУП",40,top+158+8);
  for(int i=0;i<9;i++){var at=BackpackLayout.position(36+i);g.drawString(""+(i+1),at.x()+5,top+168+8);}
  String[] names={"battery","goggles","headlamp","meteoresonator","radio","circuit_linker"};
  for(int i=0;i<names.length;i++){
   var at=BackpackLayout.position(i<3?9+i:36+i-3);
   g.drawImage(ImageIO.read(assets.resolve("textures/item/"+names[i]+".png").toFile()),at.x(),top+at.y(),16,16,null);
  }
  g.dispose();
  BufferedImage scaled=new BufferedImage(1280,960,BufferedImage.TYPE_INT_ARGB);g=scaled.createGraphics();
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
  g.drawImage(image,0,0,1280,960,null);g.dispose();
  Path out=Path.of("audit-3.12.3/layout-preview.png");Files.createDirectories(out.getParent());ImageIO.write(scaled,"PNG",out.toFile());
  System.out.println("Layout preview (not in-game): "+out);
 }
 static void centered(Graphics2D g,String text,int center,int y){g.drawString(text,center-g.getFontMetrics().stringWidth(text)/2,y+8);}
}
