package dev.whiteout.power.lobby;
 
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
 
/**
 * Shared visual language of every lobby screen: the investigation board.
 * All textures live in assets/whiteout/textures/gui/board and are bundled with the mod as PNG resources.
 * Screens keep their own logic; this class only draws and provides styled input widgets.
 */
final class LobbyUi {
 private LobbyUi(){}
 private static Identifier tex(String name){return Identifier.of("whiteout","textures/gui/board/"+name+".png");}
 static final Identifier ROOM=tex("room"),CORK=tex("cork"),FRAME=tex("frame"),PAPER=tex("paper"),CHALK=tex("paper_dark"),STICKY=tex("sticky"),STICKY_PINK=tex("sticky_pink"),
  BUTTON=tex("button"),START=tex("button_start"),PHOTO=tex("photo_frame"),PIN=tex("pin"),TAPE=tex("tape"),STATUS=tex("status"),ICONS=tex("icons"),TOGGLE=tex("toggle");
 static final int INK=0xff2e2418,INK_SOFT=0xff62503a,PAPER_TEXT=0xff2e2418,LIGHT=0xffe9dcc0,LIGHT_DIM=0xff8c8272,AMBER=0xfff0c667,THREAD=0xffa8302a;
 /** Status LED sprites, index into status.png. */
 static final int LED_OFF=0,LED_OK=1,LED_WAIT=2,LED_BAD=3;
 /** Icon sprites, index into icons.png. */
 static final int ICON_GEAR=0,ICON_LEFT=1,ICON_RIGHT=2,ICON_CHECK=3,ICON_CROSS=4,ICON_PLUS=5,ICON_MINUS=6,ICON_LOCK=7;
 
 /** Area every board-style screen is laid out in. Minecraft guarantees at least 320x240 GUI pixels. */
 record Box(int x,int y,int w,int h){
  static Box centered(int screenW,int screenH,int maxW,int maxH){int w=Math.min(screenW-12,maxW),h=Math.min(screenH-8,maxH);return new Box((screenW-w)/2,(screenH-h)/2,w,h);}
  Box inset(int p){return new Box(x+p,y+p,w-2*p,h-2*p);}
  int right(){return x+w;}
  int bottom(){return y+h;}
 }
 
 // ---------------------------------------------------------------- primitives
 static void nine(DrawContext d,Identifier id,int tw,int th,int u,int v,int rw,int rh,int x,int y,int w,int h,int l,int t,int r,int b){
  if(w<=0||h<=0)return;
  l=Math.min(l,w/2);r=Math.min(r,w-l);t=Math.min(t,h/2);b=Math.min(b,h-t);
  int cw=w-l-r,ch=h-t-b,sw=rw-l-r,sh=rh-t-b;
  d.drawTexture(id,x,y,l,t,u,v,l,t,tw,th);
  d.drawTexture(id,x+w-r,y,r,t,u+rw-r,v,r,t,tw,th);
  d.drawTexture(id,x,y+h-b,l,b,u,v+rh-b,l,b,tw,th);
  d.drawTexture(id,x+w-r,y+h-b,r,b,u+rw-r,v+rh-b,r,b,tw,th);
  if(cw>0){d.drawTexture(id,x+l,y,cw,t,u+l,v,sw,t,tw,th);d.drawTexture(id,x+l,y+h-b,cw,b,u+l,v+rh-b,sw,b,tw,th);}
  if(ch>0){d.drawTexture(id,x,y+t,l,ch,u,v+t,l,sh,tw,th);d.drawTexture(id,x+w-r,y+t,r,ch,u+rw-r,v+t,r,sh,tw,th);}
  if(cw>0&&ch>0)d.drawTexture(id,x+l,y+t,cw,ch,u+l,v+t,sw,sh,tw,th);
 }
 private static void shadow(DrawContext d,int x,int y,int w,int h){d.fill(x+2,y+3,x+w+3,y+h+3,0x66000000);}
 
 /** Dark room behind everything, with the lamp glow on the right. */
 static void backdrop(DrawContext d,int w,int h){
  d.drawTexture(ROOM,0,0,w,h,0,0,256,144,256,144);
  d.fillGradient(0,0,w,h,0x30000000,0x80000000);
 }
 /** Cork board inside a dark wooden frame. */
 static void board(DrawContext d,Box b){
  shadow(d,b.x(),b.y(),b.w(),b.h());
  d.drawTexture(CORK,b.x()+6,b.y()+6,0,0,b.w()-12,b.h()-12,64,64);
  d.fillGradient(b.x()+6,b.y()+6,b.right()-6,b.y()+22,0x66000000,0x00000000);
  d.fillGradient(b.x()+6,b.bottom()-22,b.right()-6,b.bottom()-6,0x00000000,0x66000000);
  nine(d,FRAME,24,24,0,0,24,24,b.x(),b.y(),b.w(),b.h(),8,8,8,8);
 }
 static void paper(DrawContext d,int x,int y,int w,int h){shadow(d,x,y,w,h);nine(d,PAPER,24,24,0,0,24,24,x,y,w,h,4,4,4,4);}
 static void chalk(DrawContext d,int x,int y,int w,int h){shadow(d,x,y,w,h);nine(d,CHALK,24,24,0,0,24,24,x,y,w,h,4,4,4,4);}
 static void sticky(DrawContext d,int x,int y,int w,int h,boolean pink){
  shadow(d,x,y,w,h);nine(d,pink?STICKY_PINK:STICKY,24,24,0,0,24,24,x,y,w,h,3,3,3,3);
  d.drawTexture(TAPE,x+w/2-8,y-2,0,0,16,6,16,6);
 }
 static void pin(DrawContext d,int cx,int cy){d.fill(cx-2,cy+1,cx+4,cy+5,0x55000000);d.drawTexture(PIN,cx-4,cy-4,0,0,8,8,8,8);}
 static void led(DrawContext d,int x,int y,int kind){d.drawTexture(STATUS,x,y,kind*8,0,8,8,32,8);}
 static void icon(DrawContext d,int x,int y,int which){d.drawTexture(ICONS,x,y,which*8,0,8,8,64,8);}
 /** Polaroid border around a picture whose pixels occupy (x,y,w,h). */
 static void polaroid(DrawContext d,int x,int y,int w,int h){nine(d,PHOTO,16,18,0,0,16,18,x-3,y-3,w+6,h+9,3,3,3,6);}
 /** Red string between two pins. */
 static void thread(DrawContext d,int x0,int y0,int x1,int y1){
  int dx=Math.abs(x1-x0),dy=Math.abs(y1-y0),sx=x0<x1?1:-1,sy=y0<y1?1:-1,err=dx-dy;
  for(int guard=0;guard<2000;guard++){
   d.fill(x0+1,y0+2,x0+2,y0+3,0x44000000);d.fill(x0,y0,x0+1,y0+1,THREAD);
   if(x0==x1&&y0==y1)break;int e2=2*err;if(e2>-dy){err-=dy;x0+=sx;}if(e2<dx){err+=dx;y0+=sy;}
  }
 }
 /** Dashed rule between sections. */
 static void divider(DrawContext d,int x,int y,int w){for(int i=0;i<w;i+=4)d.fill(x+i,y,x+Math.min(i+2,w),y+1,0x66402c1c);}
 /** Dark plate behind a text field; amber outline while focused. */
 static void plate(DrawContext d,TextFieldWidget f){
  int x=f.getX()-4,y=f.getY()-4,w=f.getWidth()+8,h=f.getHeight()+8;nine(d,CHALK,24,24,0,0,24,24,x,y,w,h,4,4,4,4);
  if(f.isFocused())d.drawBorder(x,y,w,h,AMBER);
 }
 static TextFieldWidget field(TextRenderer tr,int x,int y,int w,Text label,int max){
  var f=new TextFieldWidget(tr,x+4,y+4,w-8,10,label);f.setDrawsBackground(false);f.setMaxLength(max);f.setEditableColor(0xffeadfc6);return f;
 }
 
 // ---------------------------------------------------------------- text
 /** Draws text on one line inside maxW: shrinks to 70% first, then trims with an ellipsis. align 0/1/2 = left/center/right. */
 static void fit(DrawContext d,TextRenderer tr,String text,int x,int y,int maxW,int color,boolean shadow,int align){
  if(text==null||text.isEmpty()||maxW<=0)return;
  float s=1f;int tw=tr.getWidth(text);if(tw>maxW)s=Math.max(.7f,(float)maxW/tw);
  String str=text;
  if(tr.getWidth(str)*s>maxW){while(str.length()>1&&tr.getWidth(str+"…")*s>maxW)str=str.substring(0,str.length()-1);str=str.stripTrailing()+"…";}
  int w=(int)(tr.getWidth(str)*s),ox=align==1?(maxW-w)/2:align==2?maxW-w:0;
  var m=d.getMatrices();m.push();m.translate(x+ox,y+(9-9*s)/2f,0);m.scale(s,s,1f);d.drawText(tr,str,0,0,color,shadow);m.pop();
 }
 static void fit(DrawContext d,TextRenderer tr,Text text,int x,int y,int maxW,int color,boolean shadow,int align){fit(d,tr,text.getString(),x,y,maxW,color,shadow,align);}
 /** Small caps-style caption (75% size). */
 static void caption(DrawContext d,TextRenderer tr,String text,int x,int y,int maxW,int color){
  var m=d.getMatrices();m.push();m.translate(x,y,0);m.scale(.75f,.75f,1f);
  String s=text;int lim=(int)(maxW/.75f);while(s.length()>1&&tr.getWidth(s)>lim)s=s.substring(0,s.length()-1);
  d.drawText(tr,s,0,0,color,false);m.pop();
 }
 
 // ---------------------------------------------------------------- widgets
 /** Board-style button: Normal / Hover(or focus) / Pressed / Selected / Disabled. */
 static class Btn extends ButtonWidget{
  boolean selected;final boolean primary;final int icon;private long pressedUntil;
  Btn(Text text,int x,int y,int w,int h,Runnable action,boolean primary,int icon){
   super(x,y,w,h,text,b->action.run(),s->s.get());this.primary=primary;this.icon=icon;
  }
  @Override public void onPress(){pressedUntil=Util.getMeasuringTimeMs()+110;super.onPress();}
  @Override protected void renderWidget(DrawContext d,int mx,int my,float dt){
   var tr=net.minecraft.client.MinecraftClient.getInstance().textRenderer;
   int state=!active?4:Util.getMeasuringTimeMs()<pressedUntil?2:selected?3:isHovered()||isFocused()?1:0;
   int x=getX(),y=getY(),w=getWidth(),h=getHeight();
   if(state!=2&&active)d.fill(x+1,y+2,x+w+1,y+h+2,0x55000000);
   nine(d,primary?START:BUTTON,32,100,0,state*20,32,20,x,y+(state==2?1:0),w,h,4,4,4,4);
   int color=!active?LIGHT_DIM:selected?AMBER:primary?0xfffff0d6:LIGHT,off=state==2?1:0;
   int ty=y+(h-9)/2+off;String label=getMessage().getString();
   if(icon>=0&&label.isEmpty()){icon(d,x+(w-8)/2,y+(h-8)/2+off,icon);}
   else if(icon>=0){int room=w-24;int tw=Math.min(room,tr.getWidth(label));int start=x+(w-tw-12)/2;icon(d,start,y+(h-8)/2+off,icon);fit(d,tr,label,start+12,ty,room,color,true,0);}
   else fit(d,tr,label,x+5,ty,w-10,color,true,1);
   if(isFocused())d.drawBorder(x-1,y-1,w+2,h+2,0x99f0c667);
  }
 }
 static Btn button(Text t,int x,int y,int w,int h,Runnable r){return new Btn(t,x,y,w,h,r,false,-1);}
 static Btn primary(Text t,int x,int y,int w,int h,Runnable r){return new Btn(t,x,y,w,h,r,true,-1);}
 static Btn iconButton(int icon,Text label,int x,int y,int w,int h,Runnable r){return new Btn(label,x,y,w,h,r,false,icon);}
 
 /** One entry of a button row. */
 record Spec(Text label,int weight,Runnable action,int icon){
  Spec(Text label,int weight,Runnable action){this(label,weight,action,-1);}
 }
 /** Lays buttons out left-to-right across width w proportionally to their weights; never overlaps. */
 static java.util.List<Btn> row(Consumer<Btn> add,int x,int y,int w,int h,int gap,Spec... specs){
  int total=0;for(var s:specs)total+=s.weight();int free=w-gap*(specs.length-1),used=0,cx=x;var out=new java.util.ArrayList<Btn>();
  for(int i=0;i<specs.length;i++){
   int bw=i==specs.length-1?free-used:free*specs[i].weight()/total;used+=bw;
   var b=new Btn(specs[i].label(),cx,y,bw,h,specs[i].action(),false,specs[i].icon());add.accept(b);out.add(b);cx+=bw+gap;
  }
  return out;
 }
 
 /** Labelled on/off switch drawn on a paper strip. */
 static class Toggle extends ButtonWidget{
  private final BooleanSupplier value;
  Toggle(Text label,int x,int y,int w,int h,BooleanSupplier value,Runnable action){
   super(x,y,w,h,label,b->action.run(),s->s.get());this.value=value;
  }
  @Override protected void renderWidget(DrawContext d,int mx,int my,float dt){
   var tr=net.minecraft.client.MinecraftClient.getInstance().textRenderer;int x=getX(),y=getY(),w=getWidth(),h=getHeight();boolean on=value.getAsBoolean();
   paper(d,x,y,w,h);
   if(isHovered()||isFocused())d.fill(x+2,y+2,x+w-2,y+h-2,0x22ffffff);
   fit(d,tr,getMessage(),x+8,y+(h-9)/2,w-52,PAPER_TEXT,false,0);
   d.drawTexture(TOGGLE,x+w-36,y+(h-14)/2,0,on?14:0,28,14,28,28);
   if(isFocused())d.drawBorder(x-1,y-1,w+2,h+2,AMBER);
  }
 }
}