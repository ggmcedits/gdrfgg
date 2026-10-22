# Villager News – Forge 1.20.1 port (personal use)

An unofficial port of the Bedrock **Villager News** add-on by Oreville Studios / Element Animation to Minecraft Java 1.20.1 with Forge 47.
You need to own the original add-on: its models, textures, animations and voice lines are bundled here. Don't share the built jar or this project.

## Building

Requirements: **JDK 17** and an internet connection. Gradle downloads itself, Forge and Minecraft the first time.

```
./gradlew build          (Windows: gradlew.bat build)
```

The mod jar is written to `build/libs/villagernews-1.0.0.jar`. Put it in your `mods` folder with Forge 1.20.1 (47.x). No other mods are needed. GeckoLib isn't used either, because the mod includes its own runtime for Bedrock models and animations.

To test in a dev client, run `./gradlew runClient`.

## What's in it

* **The new villager look.** Every vanilla villager uses the add-on's models, textures and animations: professions, biomes, levels, babies, sleeping, riding, nose on or off, costumes and signs. The wandering trader also gets its new model. You can turn this off with *Replace villager models* in the handbook or in the client config.
* **Voice lines.** There are 523 dialogs and about 2,200 voiced clips, with subtitles. They follow the add-on's cooldowns, chattiness and rare-line settings. Villagers react to:
  * getting hurt, by weapon or cause
  * deaths nearby, trading and their reputation with you
  * blocks you break, place or use, and things you throw them
  * name tags such as Dinnerbone, jeb_ and Grumm, and leads
  * mobs they see, the weather, time skips and the real-world date
  * sleeping and being woken up, getting a job or levelling up
  * boats and minecarts, panicking, campfire stories and two-villager conversations
  * what you're wearing, potion effects and your armour
* **Special characters**, each with a spawn egg and a trade:
  * Mayor
  * Testificate Man
  * Villager #5
  * Villager #9
  * "Can't Catch Me!"
  * Wooly the sheep

  When *Spawn special villagers* is on, each character also appears once per world. It replaces a villager in a village generated more than 1,000 blocks from world spawn.
* **Items:**
  * Villager News Handbook (crafted from 3 paper): opens the settings screen
  * Mayor Hat, Testificate Man Helmet, Microphone and Moustache: wear them, or put them on villagers
  * Villager Nose
* **Interactions on villagers:**
  * Use a costume item to dress a villager.
  * Use a standing sign to have it hold the sign. An axe changes the sign's text (sneak to go backwards).
  * Shears take off the sign, then the costume, then the nose.
  * Use a Villager Nose to give the nose back.

## Settings

The **handbook** in game, or `config/villagernews-common.toml` and `villagernews-client.toml`:

| Setting | Values |
|---|---|
| Chattiness | 0 muted · 1 shy · 2 default · 3 chatterbox |
| Rare lines | 0 never · 1 default · 2 often |
| Voice range | blocks (default 16) |
| Spawn special villagers | true / false |
| Style (client) | Vanilla · Actions & Stuff · Actions & Stuff Flat |
| Subtitles / voice volume / replace villager models (client) | |

On a server, only operators can change the common settings from the handbook.

## Notes

* This was compile-checked against Minecraft 1.20.1 and Forge 47 API stubs, not a full Forge workspace. If `./gradlew build` reports a compile error, it should be a small API mismatch.
* The add-on's Bedrock data lives in `src/main/resources/assets/villagernews/bedrock/` and is interpreted at runtime. The Molang, animation controllers and render controllers are all included.
