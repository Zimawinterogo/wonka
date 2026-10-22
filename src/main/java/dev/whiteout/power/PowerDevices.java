package dev.whiteout.power;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class PowerDevices implements ModInitializer {
 public static Identifier id(String path){return Identifier.of("whiteout",path);}
 // Registered on both sides so the particle atlas can load custom feathered sprites.
 public static final SimpleParticleType GROUND_SNOW_PARTICLE=Registry.register(Registries.PARTICLE_TYPE,id("ground_snow_wisp"),FabricParticleTypes.simple());
 public static final PoweredLantern LINKED_LANTERN=Registry.register(Registries.BLOCK,id("linked_lantern"),new PoweredLantern(AbstractBlock.Settings.copy(Blocks.LANTERN).luminance(s->s.get(PoweredLantern.LIT)?15:0)));
 public static final DeviceBlock GENERATOR=block("generator",0);
 public static final DeviceBlock OLD_GENERATOR=block("old_generator",3);
 public static final DeviceBlock PANEL=block("fuse_panel",1);
 public static final DeviceBlock SHELF=block("storage_shelf",2);
 public static final Item FUEL=item("fuel_canister",new Item(new Item.Settings().maxCount(1)));
 public static final Item EMPTY_CAN=item("empty_canister",new Item(new Item.Settings().maxCount(16)));
 public static final Item FUSE=item("fuse",new Item(new Item.Settings().maxCount(16)));
 public static final Item UNSTABLE_FUSE=item("unstable_fuse",new Item(new Item.Settings().maxCount(16)));
 // Creator-only wireless binding tool. No power radius: each target is linked by position.
 public static final Item CIRCUIT_LINKER=item("circuit_linker",new Item(new Item.Settings().maxCount(1)));
 // Visual-only inventory-less representation rendered in the installed panel.
 public static final Item BURNT_FUSE=item("burnt_fuse",new Item(new Item.Settings().maxCount(1)));
 // The original 32x32 spruce plank pattern, aged for abandoned fishing cabins.
 public static final Block ROTTEN_SPRUCE_PLANKS=buildingBlock("rotten_spruce_planks",new Block(AbstractBlock.Settings.copy(Blocks.SPRUCE_PLANKS)));
 public static final PowerSwitchBlock POWER_SWITCH=Registry.register(Registries.BLOCK,id("power_switch"),new PowerSwitchBlock(AbstractBlock.Settings.copy(Blocks.LEVER).nonOpaque()));
 public static final BackupLampBlock EMERGENCY_LAMP=Registry.register(Registries.BLOCK,id("emergency_lamp"),new BackupLampBlock(AbstractBlock.Settings.copy(Blocks.REDSTONE_LAMP).nonOpaque().luminance(s->s.get(BackupLampBlock.LIT)?10:0)));
 public static final BlockEntityType<DeviceEntity> ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,id("power_device"),FabricBlockEntityTypeBuilder.create(DeviceEntity::new,GENERATOR,PANEL,SHELF,OLD_GENERATOR).build());
 public static final BlockEntityType<BackupLampEntity> EMERGENCY_LAMP_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,id("emergency_lamp_entity"),FabricBlockEntityTypeBuilder.create(BackupLampEntity::new,EMERGENCY_LAMP).build());
 public static final Block RIME_GLASS_PANE=buildingBlock("rime_glass_pane",new PaneBlock(AbstractBlock.Settings.copy(Blocks.GLASS_PANE).nonOpaque()));
 public static final Block FROST_WINDOW=buildingBlock("frost_window",new FrostWindowBlock(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque().ticksRandomly()));
 public static final Block THERMOMETER=buildingBlock("wall_thermometer",new ThermometerBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));
 public static final SoundEvent ENGINE=sound("generator_engine"),PULL=sound("generator_pull"),START=sound("generator_start"),STOP=sound("generator_stop"),CLICK=sound("fuse_click"),FAIL=sound("generator_fail"),OLD_GEN_LOOP=sound("old_generator_loop"),OLD_GEN_START=sound("old_generator_start"),OLD_GEN_STOP=sound("old_generator_stop"),OLD_GEN_CATCH=sound("old_generator_catch"),OLD_GEN_FAIL=sound("old_generator_fail"),OLD_CREAK=sound("old_house_creak"),OLD_RATTLE=sound("old_window_rattle"),OLD_SLAM=sound("old_door_slam"),ICE_CRACK=sound("door_ice_crack");
 // Keep generator_fail unchanged: the electrical outage also uses that event.
 public static final SoundEvent MODERN_GEN_FAIL=sound("modern_generator_fail");
 static Block buildingBlock(String name,Block block){
  Registry.register(Registries.BLOCK,id(name),block);
  Registry.register(Registries.ITEM,id(name),new BlockItem(block,new Item.Settings()));
  return block;
 }
 static DeviceBlock block(String name,int kind){
  DeviceBlock b=Registry.register(Registries.BLOCK,id(name),new DeviceBlock(kind,AbstractBlock.Settings.create().strength(3.0f).nonOpaque().sounds(net.minecraft.sound.BlockSoundGroup.METAL).luminance(s->s.get(DeviceBlock.ACTIVE)?(kind==1?3:1):0)));
  Registry.register(Registries.ITEM,id(name),new BlockItem(b,new Item.Settings()));return b;
 }
 static Item item(String name,Item item){return Registry.register(Registries.ITEM,id(name),item);}
 static SoundEvent sound(String name){return Registry.register(Registries.SOUND_EVENT,id(name),SoundEvent.of(id(name)));}
 @Override public void onInitialize(){
  dev.whiteout.power.backpack.BackpackNetwork.register();
  Registry.register(Registries.ITEM,id("power_switch"),new BlockItem(POWER_SWITCH,new Item.Settings()));
  Registry.register(Registries.ITEM,id("emergency_lamp"),new BlockItem(EMERGENCY_LAMP,new Item.Settings()));
  Registry.register(Registries.ITEM_GROUP,id("power_objects"),FabricItemGroup.builder().displayName(Text.translatable("itemGroup.whiteout.power_objects")).icon(()->new ItemStack(GENERATOR)).entries((c,e)->{e.add(GENERATOR);e.add(OLD_GENERATOR);e.add(PANEL);e.add(SHELF);e.add(EMERGENCY_LAMP);e.add(POWER_SWITCH);e.add(FUEL);e.add(EMPTY_CAN);e.add(FUSE);e.add(UNSTABLE_FUSE);e.add(CIRCUIT_LINKER);}).build());
  Registry.register(Registries.ITEM_GROUP,id("building_materials"),FabricItemGroup.builder().displayName(Text.translatable("itemGroup.whiteout.building_materials")).icon(()->new ItemStack(ROTTEN_SPRUCE_PLANKS)).entries((c,e)->{e.add(ROTTEN_SPRUCE_PLANKS);e.add(RIME_GLASS_PANE);e.add(FROST_WINDOW);e.add(THERMOMETER);}).build());
  EnvironmentNetwork.register();
  WorldAtmosphere.register();
  PowerNetwork.register();
  PowerDiagnostics.register();
  GridManager.register();
  PowerSwitches.register();
  ElectricDoors.register();
  AutoClosingDoors.register();
  WindMovement.register();
  DoorGateSync.register();
  ZoneFolders.register();
  EditorNetwork.register();
  ZoneSettingsCopy.register();
 }
}
