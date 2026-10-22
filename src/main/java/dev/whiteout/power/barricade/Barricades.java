package dev.whiteout.power.barricade;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Registration only. No ticks, no scanning, no saved data: all state lives in block states. */
public final class Barricades implements ModInitializer {
 private static Identifier id(String p){return Identifier.of("whiteout",p);}
 public static final BarricadeBlock BARRICADE=Registry.register(Registries.BLOCK,id("barricade"),
  new BarricadeBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(1.5f,3.0f).nonOpaque().dynamicBounds()
   .sounds(BlockSoundGroup.WOOD).pistonBehavior(PistonBehavior.DESTROY)));
 public static final Item BOARDS=Registry.register(Registries.ITEM,id("barricade_boards"),new BarricadeItem(BARRICADE,new Item.Settings()));
 @Override public void onInitialize(){
  ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP,id("building_materials"))).register(e->e.add(BOARDS));
 }
}
