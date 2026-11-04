package dev.whiteout.power.lobby;

import dev.whiteout.power.SharpScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.option.SoundOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.nio.file.*;

public final class PersonalScreen extends SharpScreen{
 public record Preferences(int letters,boolean portraits,boolean hints){public Preferences{if(letters<1||letters>4)throw new IllegalArgumentException("text speed");}}
 private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("whiteout-reading.json");
 private static Preferences prefs=load();
 public static Preferences prefs(){return prefs;}
 private static Preferences load(){try{if(Files.isRegularFile(FILE)&&Files.size(FILE)<1024){var value=BoardNetwork.JSON.fromJson(Files.readString(FILE),Preferences.class);if(value!=null)return value;}}catch(Exception ignored){}return new Preferences(1,true,true);}
 private final BoardNetwork.View board;private String error="";
 PersonalScreen(BoardNetwork.View board){super(Text.literal("Личные настройки"));this.board=board;}
 private void save(Preferences changed){try{var tmp=Files.createTempFile(FILE.getParent(),"whiteout-reading-",".tmp");try{Files.writeString(tmp,BoardNetwork.JSON.toJson(changed));Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(tmp);}prefs=changed;clearAndInit();}catch(Exception ex){error="Не удалось сохранить настройки";}}
 private void btn(String label,int row,Runnable action){addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(width/2-100,height/2-56+row*25,200,20).build());}
 @Override protected void init(){btn("Скорость текста · "+prefs.letters()+"×",0,()->save(new Preferences(prefs.letters()%4+1,prefs.portraits(),prefs.hints())));
  btn("Портреты · "+(prefs.portraits()?"да":"нет"),1,()->save(new Preferences(prefs.letters(),!prefs.portraits(),prefs.hints())));
  btn("Подсказки доски · "+(prefs.hints()?"да":"нет"),2,()->save(new Preferences(prefs.letters(),prefs.portraits(),!prefs.hints())));
  btn("Музыка и звуки",3,()->client.setScreen(new SoundOptionsScreen(this,client.options)));}
 @Override public void render(DrawContext d,int mx,int my,float dt){d.fill(0,0,width,height,0xf1322921);d.drawCenteredTextWithShadow(textRenderer,error.isBlank()?"Личные настройки":error,width/2,height/2-78,0xffdfcbaa);super.render(d,mx,my,dt);}
 @Override public void close(){client.setScreen(new BoardScreen(board));}
}
