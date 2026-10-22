package dev.whiteout.power.mixin;

import dev.whiteout.Zone;
import dev.whiteout.client.ZoneScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the folder UI open the zone editor with a zone already selected (what a click in the zone list does). */
@Mixin(value=ZoneScreen.class,remap=false)
public interface ZoneScreenAccess {
 @Accessor(value="draft",remap=false) Zone whiteout$getDraft();
 @Accessor(value="draft",remap=false) void whiteout$setDraft(Zone zone);
 @Accessor(value="listPage",remap=false) void whiteout$setListPage(int page);
}
