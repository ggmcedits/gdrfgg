# LT Bloody Fiend – Forge 1.20.1 port of `lt:bloody_fiend`

Source: `LTS_v148 (1.21.100)` add-on. Mod id `lt`, so the entity id stays `lt:bloody_fiend`
(`/summon lt:bloody_fiend`, spawn egg in the Spawn Eggs tab).

## Build
Requires JDK 17, Forge 1.20.1 (47.3.0), GeckoLib 4.4.9 (downloaded by Gradle).
Easiest: take the official Forge 1.20.1 MDK for its `gradlew` + `gradle/` folder, copy them in
next to `build.gradle`, then `./gradlew build`. The jar is in `build/libs/`.
**This project was written without network access and has NOT been compiled or run.**
Expect to fix a few API/typo errors on first build; see "Unverified" below.

## Copied byte-for-byte from the add-on
- `bloody_fiend.geo.json` (model)   - `bloody_fiend.animation.json` (all 21 animations)
- `bloody_fiend.png`, spawn-egg texture, ambient/hurt/death/smash sounds (re-encoded to mono ogg
  so Java applies distance falloff), en_US / zh_CN names.

## Particles
The Bloody Fiend has **no** custom particles in the add-on: its client entity, animations and
script never spawn any (the pack's particle files belong to other mobs). None were added or substituted.

## Behaviour transcribed from the add-on
Slumber -> wakes when hit -> awaken roar -> phase 1 (660 HP) -> death = "play dead" cinematic
(step dashes, camera shakes) -> crouch phase (new 660 HP) -> corpse (falls, 101/49 crush damage)
-> blaze powder (50 %) opens mouth -> soul sand. All timers, 8 standing skills, 4 crouch skills,
random-event weights and distance bands, damage numbers, knockback numbers, slowness/blindness/nausea,
anti_healing + blood_curse, lifesteal (devour, curse, kills), damage resistance per 10 % HP lost,
effect immunity, block breaking, 64-block boss bar, footstep camera shake.

## Judgement calls / known differences
1. Camera shake is a client camera-angle jitter (Forge has no camera-position hook without mixins).
2. Add-on bugs kept as-is: `crouch.attack` hits targets *behind* it (`inFront` check is inverted);
   after the first `behavior_reset` crouch speed is 0.20 not 0.30; devour's second hit is NaN and never lands.
3. Assumed (not determinable from the files): `selfDestruct` ignores armor; `break_blocks` drops items;
   health `min: 1` is treated as normal death at 0 HP; `minecraft:scale` scales the hitbox.
4. `play_dead` behaviour (regeneration) is omitted; the boss clears effects every tick anyway.
5. Bedrock `applyKnockback/applyImpulse` are mapped to setting/adding velocity with the same numbers.
6. The add-on's loot table path does not exist and the pearl event is empty, so there are no drops.

## Unverified (need a real build)
GeckoLib 4.4.9 calls: `withScale`, `transitionLength`, `setAnimationSpeed`, `Animation.LoopType.DEFAULT`.
