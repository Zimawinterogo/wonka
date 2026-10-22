Whiteout 3.12.7 - barricade visual/attachment fix (sources only)

Based on Whiteout-3.12.7-GroundSnow-Wisps-Compile-Fix-Sources.zip.
Version remains 3.12.7; existing weather and other features are unchanged.

Fixes:
- Removed the unwanted full-height vertical battens from barricade
  model variants (especially paired top/bottom variants).
- Kept the original horizontal plank shapes, texture, nails and orientation.
- Rebuilt BarricadeLayouts collision boxes/coverage to match models.
- Panels can now be extended vertically or along the wall's horizontal axis
  from existing panels, but only while a path reaches a wall-framed cell.
- All neighbour updates now recheck support to avoid suspended orphan panels.
- Kept every barricade variant windproof using the updated coverage threshold.
- New panels inherit the facing of adjacent panels, including opposite-facing orientations.

Updated sources: BarricadeBlock.java, BarricadePlanner.java, BarricadeApi.java, BarricadeLayouts.java,
  7 block-model JSON files and tools/make_barricade_assets.py.
No other Java source files or textures changed.

Tests performed:
- All 9 barricade model JSONs parsed; no full-height support batten remains.
- Generated BarricadeLayouts.java compiled by Java 21 standalone.
- The compiled layout arrays matched the calculated model coverage.
- All resource JSONs parsed and both ZIPs passed integrity checks.
NOT performed: full Fabric/Gradle build, Minecraft/BSL visual test.

Manual tests on a WORLD COPY:
1. Compare a single barricade and a 1x2 barricade in all four directions.
   No vertical strips should stick out above or below boards.
2. Check all variants (replace multiple times, different positions).
3. Extend a panel sideways and up/down in a wide opening.
4. Break the original wall frame; unsupported panel chains should not remain.
5. Reload the world: ensure facing, model, and shapes remain correct.
6. Verify boarding item cost, drops, collision, and shelter interaction.

Patch: overlay these relative paths on the SAME up-to-date 3.12.7 sources,
then compile. Do not apply to 3.12.3 or 3.12.5.
