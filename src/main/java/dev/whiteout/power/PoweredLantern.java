package dev.whiteout.power;

import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;

/** Only explicitly bound vanilla lanterns are converted to this block. */
public final class PoweredLantern extends LanternBlock {
 public static final BooleanProperty LIT = Properties.LIT;
 public PoweredLantern(Settings settings) {
  super(settings);
  setDefaultState(getDefaultState().with(LIT, false));
 }
 @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
  super.appendProperties(builder);
  builder.add(LIT);
 }
}
