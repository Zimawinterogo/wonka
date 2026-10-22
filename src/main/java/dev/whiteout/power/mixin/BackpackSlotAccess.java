package dev.whiteout.power.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Only presentation coordinates; restored when the backpack closes. */
@Mixin(Slot.class)
public interface BackpackSlotAccess {
 @Mutable @Accessor("x") void whiteout$setX(int value);
 @Mutable @Accessor("y") void whiteout$setY(int value);
}
