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
 * WHITEOUT dossier: client-only visual language.
 * All buttons stay real Minecraft widgets. All story permissions and network calls
 * remain owned by the original screens.
 */
final class LobbyUi {
 private LobbyUi(){}
 private static Identifier tex(String name){return Identifier.of("whiteout","textures/gui/board/"+name+".png");}
 static final Identifier ROOM=tex("room"),CORK=tex("cork"),FRAME=tex("frame"),PAPER=tex("paper"),CHALK=tex("paper_dark"),
  STICKY=tex("sticky"),STICKY_PINK=tex("sticky_pink"),BUTTON=tex("button"),START=tex("button_start"),
  PHOTO=tex("photo_frame"),PIN=tex("pin"),TAPE=tex("tape"),STATUS=tex("status"),ICONS=tex("icons"),TOGGLE=tex("toggle");
 // Cold steel, readable off-white and an emergency-red launch accent.
 static final int INK=0xffe9f1f7,INK_SOFT=0xffb2c1cc,PAPER_TEXT=0xffe9f1f7,
  LIGHT=0xfff2f6f7,LIGHT_DIM=0xffa3b2bf,AMBER=0xffedc481,THREAD=0xffae3a3a;
 static final int LED_OFF=0,LED_OK=1,LED_WAIT=2,LED_BAD=3;
 static final int ICON_GEAR=0,ICON_LEFT=1,ICON_RIGHT=2,ICON_CHECK=3,ICON_CROSS=4,ICON_PLUS=5,ICON_MINUS=6,ICON_LOCK=7;
 // Minecraft's uniform Unicode face keeps Russian captions at native 1x rasterization.
 static final Identifier UI_FONT=Identifier.of("minecraft","uniform");

 record Box(int x,int y,int w,int h){
  static Box centered(int screenW,int screenH,int maxW,int maxH){
   int w=Math.min(screenW-12,maxW),h=Math.min(screenH-8,maxH);
   return new Box((screenW-w)/2,(screenH-h)/2,w,h);
  }
  Box inset(int p){return new Box(x+p,y+p,w-2*p,h-2*p);}
  int right(){return x+w;}
  int bottom(){return y+h;}
 }

 /** Nine-sliced material. Never stretch 1-pixel corners over large panels. */
 static void nine(DrawContext d,Identifier id,int tw,int th,int u,int v,int rw,int rh,
    int x,int y,int w,int h,int l,int t,int r,int b){
  if(w<=0||h<=0)return;
  l=Math.min(l,w/2);r=Math.min(r,w-l);t=Math.min(t,h/2);b=Math.min(b,h-t);
  int cw=w-l-r,ch=h-t-b,sw=rw-l-r,sh=rh-t-b;
  d.drawTexture(id,x,y,l,t,u,v,l,t,tw,th);
  d.drawTexture(id,x+w-r,y,r,t,u+rw-r,v,r,t,tw,th);
  d.drawTexture(id,x,y+h-b,l,b,u,v+rh-b,l,b,tw,th);
  d.drawTexture(id,x+w-r,y+h-b,r,b,u+rw-r,v+rh-b,r,b,tw,th);
  if(cw>0){d.drawTexture(id,x+l,y,cw,t,u+l,v,sw,t,tw,th);
   d.drawTexture(id,x+l,y+h-b,cw,b,u+l,v+rh-b,sw,b,tw,th);}
  if(ch>0){d.drawTexture(id,x,y+t,l,ch,u,v+t,l,sh,tw,th);
   d.drawTexture(id,x+w-r,y+t,r,ch,u+rw-r,v+t,r,sh,tw,th);}
  if(cw>0&&ch>0)d.drawTexture(id,x+l,y+t,cw,ch,u+l,v+t,sw,sh,tw,th);
 }
 private static void shadow(DrawContext d,int x,int y,int w,int h){
  d.fill(x+4,y+6,x+w+5,y+h+6,0x70000000);
  d.fill(x+2,y+3,x+w+2,y+h+3,0x49000000);
 }
 static void backdrop(DrawContext d,int w,int h){
  d.drawTexture(ROOM,0,0,w,h,0,0,512,288,512,288);
  d.fillGradient(0,0,w,h,0x1703080c,0x8304070b);
 }
 static void board(DrawContext d,Box b){
  shadow(d,b.x(),b.y(),b.w(),b.h());
  d.drawTexture(CORK,b.x()+6,b.y()+6,0,0,b.w()-12,b.h()-12,64,64);
  d.fillGradient(b.x()+8,b.y()+8,b.right()-8,b.bottom()-8,0x1f264458,0x56202b33);
  nine(d,FRAME,32,32,0,0,32,32,b.x(),b.y(),b.w(),b.h(),10,10,10,10);
  d.fill(b.x()+12,b.y()+8,b.right()-12,b.y()+9,0x887b9aa9);
  d.fill(b.x()+13,b.bottom()-9,b.right()-13,b.bottom()-8,0x77445565);
  d.fill(b.x()+12,b.y()+9,b.x()+14,b.bottom()-9,0x665c7380);
 }
 static void paper(DrawContext d,int x,int y,int w,int h){
  shadow(d,x,y,w,h);nine(d,PAPER,32,32,0,0,32,32,x,y,w,h,7,7,7,7);
 }
 static void chalk(DrawContext d,int x,int y,int w,int h){
  shadow(d,x,y,w,h);nine(d,CHALK,32,32,0,0,32,32,x,y,w,h,7,7,7,7);
 }
 static void sticky(DrawContext d,int x,int y,int w,int h,boolean pink){
  shadow(d,x,y,w,h);nine(d,pink?STICKY_PINK:STICKY,32,32,0,0,32,32,x,y,w,h,7,7,7,7);
  d.drawTexture(TAPE,x+w/2-8,y-3,0,0,16,6,16,6);
 }
 static void pin(DrawContext d,int cx,int cy){
  d.fill(cx-2,cy+2,cx+4,cy+5,0x50000000);
  d.drawTexture(PIN,cx-4,cy-4,0,0,8,8,8,8);
 }
 static void led(DrawContext d,int x,int y,int kind){d.drawTexture(STATUS,x,y,kind*8,0,8,8,32,8);}
 static void icon(DrawContext d,int x,int y,int which){d.drawTexture(ICONS,x,y,which*8,0,8,8,64,8);}
 static void polaroid(DrawContext d,int x,int y,int w,int h){
  nine(d,PHOTO,24,24,0,0,24,24,x-3,y-3,w+6,h+9,4,4,4,7);
 }
 static void thread(DrawContext d,int x0,int y0,int x1,int y1){
  int dx=Math.abs(x1-x0),dy=Math.abs(y1-y0),sx=x0<x1?1:-1,sy=y0<y1?1:-1,err=dx-dy;
  for(int guard=0;guard<2000;guard++){
   d.fill(x0+1,y0+2,x0+2,y0+3,0x33000000);d.fill(x0,y0,x0+1,y0+1,THREAD);
   if(x0==x1&&y0==y1)break;
   int e2=2*err;if(e2>-dy){err-=dy;x0+=sx;}if(e2<dx){err+=dx;y0+=sy;}
  }
 }
 static void divider(DrawContext d,int x,int y,int w){
  if(w<=0)return;
  d.fill(x,y,x+w,y+1,0x8a547184);
  d.fill(x,y+1,x+w,y+2,0x392c4656);
 }
 static void accent(DrawContext d,int x,int y,int h,int color){d.fill(x,y,x+3,y+h,color);}
 static void plate(DrawContext d,TextFieldWidget f){
  int x=f.getX()-4,y=f.getY()-4,w=f.getWidth()+8,h=f.getHeight()+8;
  nine(d,CHALK,32,32,0,0,32,32,x,y,w,h,6,6,6,6);
  if(f.isFocused())d.drawBorder(x,y,w,h,AMBER);
 }
 static TextFieldWidget field(TextRenderer tr,int x,int y,int w,Text label,int max){
  var f=new TextFieldWidget(tr,x+4,y+4,w-8,10,label);
  f.setDrawsBackground(false);f.setMaxLength(max);f.setEditableColor(LIGHT);return f;
 }

 // Render *native-resolution* glyphs: do not shrink text to 70%, which previously
 // caused blurred, jagged Cyrillic. Ellipsize long labels instead.
 private static Text styled(String s){return Text.literal(s).styled(style->style.withFont(UI_FONT));}
 static void fit(DrawContext d,TextRenderer tr,String text,int x,int y,int maxW,int color,boolean sh,int align){
  if(text==null||text.isEmpty()||maxW<=1)return;
  String value=text;
  Text rendered=styled(value);
  if(tr.getWidth(rendered)>maxW){
   while(value.length()>1&&tr.getWidth(styled(value+"…"))>maxW)value=value.substring(0,value.length()-1);
   value=value.stripTrailing()+"…";rendered=styled(value);
  }
  int tw=tr.getWidth(rendered);
  int dx=align==1?(maxW-tw)/2:align==2?maxW-tw:0;
  d.drawText(tr,rendered,x+Math.max(0,dx),y,color,sh);
 }
 static void fit(DrawContext d,TextRenderer tr,Text text,int x,int y,int maxW,int color,boolean sh,int align){
  fit(d,tr,text.getString(),x,y,maxW,color,sh,align);
 }
 static void caption(DrawContext d,TextRenderer tr,String text,int x,int y,int maxW,int color){
  fit(d,tr,text,x,y,maxW,color,false,0);
 }

 /** Button has real keyboard focus, click handling, and native input semantics. */
 static class Btn extends ButtonWidget{
  boolean selected;final boolean primary;final int icon;private long pressedUntil;
  Btn(Text text,int x,int y,int w,int h,Runnable action,boolean primary,int icon){
   super(x,y,w,h,text,b->action.run(),supplier->supplier.get());
   this.primary=primary;this.icon=icon;
  }
  @Override public void onPress(){pressedUntil=Util.getMeasuringTimeMs()+110;super.onPress();}
  @Override protected void renderWidget(DrawContext d,int mx,int my,float dt){
   var tr=net.minecraft.client.MinecraftClient.getInstance().textRenderer;
   int state=!active?4:Util.getMeasuringTimeMs()<pressedUntil?2:selected?3:isHovered()||isFocused()?1:0;
   int x=getX(),y=getY(),w=getWidth(),h=getHeight(),shift=state==2?1:0;
   if(active)d.fill(x+2,y+3,x+w+3,y+h+3,0x72000000);
   nine(d,primary?START:BUTTON,40,120,0,state*24,40,24,x,y+shift,w,h,6,6,6,6);
   int color=!active?0xff62737d:selected?AMBER:primary?0xfffff4ef:LIGHT;
   String label=getMessage().getString();
   if(icon>=0&&label.isEmpty())icon(d,x+(w-8)/2,y+(h-8)/2+shift,icon);
   else if(icon>=0){
    int textW=Math.max(0,w-23);
    icon(d,x+7,y+(h-8)/2+shift,icon);
    fit(d,tr,label,x+19,y+(h-9)/2+shift,textW,color,false,0);
   }else fit(d,tr,label,x+5,y+(h-9)/2+shift,w-10,color,false,1);
   if(isFocused())d.drawBorder(x-1,y-1,w+2,h+2,0xb3f2dca8);
  }
 }
 static Btn button(Text t,int x,int y,int w,int h,Runnable r){return new Btn(t,x,y,w,h,r,false,-1);}
 static Btn primary(Text t,int x,int y,int w,int h,Runnable r){return new Btn(t,x,y,w,h,r,true,-1);}
 static Btn iconButton(int i,Text t,int x,int y,int w,int h,Runnable r){return new Btn(t,x,y,w,h,r,false,i);}
 record Spec(Text label,int weight,Runnable action,int icon){
  Spec(Text label,int weight,Runnable action){this(label,weight,action,-1);}
 }
 static java.util.List<Btn> row(Consumer<Btn> add,int x,int y,int w,int h,int gap,Spec... specs){
  int total=0;for(var spec:specs)total+=spec.weight();
  int free=w-gap*(specs.length-1),used=0,cx=x;
  var out=new java.util.ArrayList<Btn>();
  for(int i=0;i<specs.length;i++){
   int bw=i==specs.length-1?free-used:free*specs[i].weight()/total;used+=bw;
   var b=new Btn(specs[i].label(),cx,y,bw,h,specs[i].action(),false,specs[i].icon());
   add.accept(b);out.add(b);cx+=bw+gap;
  }return out;
 }
 static class Toggle extends ButtonWidget{
  private final BooleanSupplier value;
  Toggle(Text label,int x,int y,int w,int h,BooleanSupplier value,Runnable action){
   super(x,y,w,h,label,b->action.run(),supplier->supplier.get());this.value=value;
  }
  @Override protected void renderWidget(DrawContext d,int mx,int my,float dt){
   var tr=net.minecraft.client.MinecraftClient.getInstance().textRenderer;
   int x=getX(),y=getY(),w=getWidth(),h=getHeight();boolean on=value.getAsBoolean();
   paper(d,x,y,w,h);
   if(isHovered()||isFocused())d.fill(x+2,y+2,x+w-2,y+h-2,0x22ffffff);
   fit(d,tr,getMessage(),x+8,y+(h-9)/2,w-52,LIGHT,false,0);
   d.drawTexture(TOGGLE,x+w-36,y+(h-14)/2,0,on?14:0,28,14,28,28);
   if(isFocused())d.drawBorder(x-1,y-1,w+2,h+2,AMBER);
  }
 }
}
