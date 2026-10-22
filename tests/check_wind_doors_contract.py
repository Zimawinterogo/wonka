"""Static integration contract checks; not an in-game Fabric runtime test."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
power=root/'src/main/java/dev/whiteout/power'
wind=(power/'WindMovement.java').read_text(encoding='utf-8')
auto=(power/'AutoClosingDoors.java').read_text(encoding='utf-8')
bolts=(power/'bolt/DoorBolts.java').read_text(encoding='utf-8')
main=(power/'PowerDevices.java').read_text(encoding='utf-8')
assert 'AutoClosingDoors.register()' in main and 'WindMovement.register()' in main
assert 'literal("whiteout-wind")' in wind and 'literal("status")' in wind
assert 'player.getEyePos()' in wind and 'WorldAtmosphere.weather(world,player.getBlockPos())' in wind
assert '!player.isOnGround()' not in wind, 'ordinary jumping must not disable wind penalty'
assert 'SharedWindField.directionX(time)' in wind and 'SharedWindField.directionZ(time)' in wind
assert 'SharedWindField.strength(time,' in wind and 'WindMovementPolicy.resistance(displacement.x,displacement.z,windX,windZ,strength)' in wind
assert (power/'GroundSnow.java').read_text(encoding='utf-8').count('SharedWindField.strength(tick,')==1
assert 'world.isSkyVisible(feet.up())' in wind and 'WorldAtmosphere.shelter(world,feet)' in wind
assert 'new EntityAttributeModifier(' in wind and 'ADD_MULTIPLIED_TOTAL' in wind
assert 'WorldAtmosphere.get(server).windMovement' in wind
assert 'AutoDoorPolicy.DELAY_TICKS' in auto and 'ElectricDoors.isManaged(world,pos)' in auto
assert 'occupied(world,pos)' in auto and 'powered(world,pos)' in auto
assert '.setOpen(null,world,state,pos,false)' in auto, 'door close must use vanilla update API'
assert 'DoorBolts::onUse' in bolts and 'if(!bolt.locked())return ActionResult.PASS' in bolts
assert 'DoorBolts.put' not in auto and 'DoorBolts.detach' not in auto, 'door closer must not mutate bolt state'
print('PASS: wind/door/bolt integration source contracts (13 assertions)')
