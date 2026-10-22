package dev.whiteout.power;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class StarterScreen extends SharpScreen {
 public final BlockPos pos;private long epoch;private int progress,status;private boolean waiting;private ButtonWidget pull;
 public StarterScreen(PowerNetwork.Starter v){super(Text.translatable("screen.whiteout.starter"));pos=v.pos();epoch=v.start();progress=v.progress();status=v.status();}
 @Override protected void init(){pull=addDrawableChild(ButtonWidget.builder(Text.translatable("screen.whiteout.pull"),b->pull()).dimensions(width/2-100,height/2+55,200,22).build());}
 public void update(PowerNetwork.Starter v){epoch=v.start();progress=v.progress();status=v.status();waiting=false;if(status==3||status==4)close();}
 private boolean worn(){return client!=null&&client.world!=null&&client.world.isChunkLoaded(pos)&&client.world.getBlockState(pos).isOf(PowerDevices.OLD_GENERATOR);}
 private int period(){return worn()?WornStarterLogic.period(progress):StarterLogic.period(progress);}
 private int windowStart(){return worn()?WornStarterLogic.windowStart(progress):StarterLogic.windowStart(progress);}
 private int windowEnd(){return worn()?WornStarterLogic.windowEnd(progress):StarterLogic.windowEnd(progress);}
 private int phase(long ticks){return worn()?WornStarterLogic.phase(ticks,progress):StarterLogic.phase(ticks,progress);}
 private void pull(){if(waiting||client==null||client.world==null||client.world.getTime()<epoch)return;waiting=true;ClientPlayNetworking.send(new PowerNetwork.Action(pos,1));}
 @Override public void tick(){
  if(client==null||client.world==null||client.player==null||!client.player.isAlive()||client.player.squaredDistanceTo(pos.toCenterPos())>36||!(client.world.getBlockEntity(pos) instanceof DeviceEntity)){close();return;}
  pull.active=!waiting&&client.world.getTime()>=epoch;
 }
 @Override public boolean shouldPause(){return false;}
 @Override public void close(){if(client!=null){if(client.getNetworkHandler()!=null)ClientPlayNetworking.send(new PowerNetwork.Action(pos,2));client.setScreen(null);}}
 @Override public boolean keyPressed(int key,int scancode,int modifiers){if(key==GLFW.GLFW_KEY_SPACE){pull();return true;}return super.keyPressed(key,scancode,modifiers);}
 @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
  c.fill(0,0,width,height,0x88070B0E);int x=width/2-150,y=height/2;
  c.fill(x-18,y-90,x+318,y+95,0xED151E23);
  c.drawCenteredTextWithShadow(textRenderer,title,width/2,y-72,0xE4ECEB);
  c.drawCenteredTextWithShadow(textRenderer,Text.translatable(worn()?"screen.whiteout.worn_instruction":"screen.whiteout.instruction"),width/2,y-48,0xAAB7BA);
  c.fill(x,y-10,x+300,y+10,0xFF303B41);
  c.fill(x+300*windowStart()/period(),y-10,x+300*windowEnd()/period(),y+10,0xFF577D55);
  if(client!=null&&client.world!=null){long elapsed=client.world.getTime()-epoch;float phase=elapsed<0?0:(phase(elapsed)+delta)/period();int marker=x+(int)(phase*300);c.fill(marker-2,y-15,marker+2,y+15,0xFFF0D8A2);}
  c.drawCenteredTextWithShadow(textRenderer,Text.translatable("screen.whiteout.progress",progress,worn()?WornStarterLogic.PULLS:StarterLogic.PULLS),width/2,y+23,0xE4ECEB);
  if(status==2)c.drawCenteredTextWithShadow(textRenderer,Text.translatable("screen.whiteout.miss"),width/2,y+39,0xD58A75);
  super.render(c,mouseX,mouseY,delta);
 }
}
