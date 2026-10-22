package dev.whiteout.power.mixin;

import dev.whiteout.Zone;
import dev.whiteout.client.ZoneScreen;
import dev.whiteout.power.EnvironmentClient;
import dev.whiteout.power.EnvironmentNetwork;
import dev.whiteout.power.OldBuildingSoundScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds an editor-only old-building toggle alongside the existing Shelter controls. */
@Mixin(value=ZoneScreen.class,remap=false)
public abstract class ZoneOldShelterMixin extends Screen {
 protected ZoneOldShelterMixin(Text title){super(title);}
 @Shadow(remap=false) private int page;
 @Shadow(remap=false) private int panel;
 @Shadow(remap=false) private int top;
 @Unique private ButtonWidget whiteout$oldShelter;
 @Inject(method={"init","method_25426"},at=@At("TAIL"),remap=false,require=0)
 private void whiteout$addOldBuilding(CallbackInfo ci){
  whiteout$oldShelter=null;
  Zone zone=((ZoneScreen)(Object)this).preview();
  if(zone==null||!zone.shelter||zone.id==null||zone.id.isBlank())return;
  // The toggle is visible only for zones marked as shelters.
  whiteout$oldShelter=ButtonWidget.builder(whiteout$label(zone.id),button->{
   Zone current=((ZoneScreen)(Object)this).preview();
   if(current==null||!current.shelter||current.id==null)return;
   boolean newValue=!EnvironmentClient.isOld(current.id);
   EnvironmentClient.setOldLocal(current.id,newValue);
   ClientPlayNetworking.send(new EnvironmentNetwork.SetOldShelter(current.id,newValue));
   button.setMessage(whiteout$label(current.id));
   // Refresh the sound tab immediately after the old-building flag changes.
   this.clearAndInit();
  }).dimensions(this.width-167,29,156,20).build();
  this.addDrawableChild(whiteout$oldShelter);
  // The existing base editor uses page 2 for its Sound tab.
  // Only expose sound controls after the shelter is marked as an old building.
  if(page==2&&EnvironmentClient.isOld(zone.id)){
   this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.whiteout.old_sound.button"),button->{
    Zone selected=((ZoneScreen)(Object)this).preview();
    if(selected==null||selected.id==null||!EnvironmentClient.isOld(selected.id))return;
    this.client.setScreen(new OldBuildingSoundScreen(this,selected.id));
   }).dimensions(panel,top+199,Math.min(260,Math.max(145,width-panel-12)),20).build());
  }
 }
 @Unique private static Text whiteout$label(String zone){return Text.translatable("screen.whiteout.old_shelter",Text.translatable(EnvironmentClient.isOld(zone)?"screen.whiteout.old_shelter.yes":"screen.whiteout.old_shelter.no"));}
}
