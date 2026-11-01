# Supported Minecraft lines

Each file here describes one Minecraft line that Angelic Halo is built for. Build a line with `-Pmc=<line>`, e.g.

```
./gradlew build -Pmc=26.1
```

which produces `build/libs/angelic-halo-<version>+26.1.jar`. Without `-Pmc` the line in `gradle.properties` (`mc_line`) is used.

| Key | Meaning |
|---|---|
| `minecraft_version` | Version compiled and tested against (the newest patch of the line) |
| `mc_range` | `fabric.mod.json` Minecraft dependency written into this jar |
| `game_versions` | Versions the jar is published for on Modrinth (each is tested in CI) |
| `fabric_api_version`, `modmenu_version` | Dependencies for that line |
| `compat` | Version-specific source folders under `src/compat/` |

The small `src/compat/` folders hold the few classes whose Minecraft API differs between lines:

- `screen_minecraft` / `screen_gui`: opening screens (`Minecraft.setScreen` in 26.1, `Gui.setScreen` from 26.2), and the Esc-menu Friends button (26.2+).
- `pipeline_blaze3d` / `pipeline_renderpearl`: render pipelines (`com.mojang.blaze3d.pipeline` up to 26.2, `com.mojang.renderpearl.api.pipeline` from 26.3).

Any single property can be overridden on the command line, e.g. `-Pminecraft_version=26.1` to test the 26.1 jar on 26.1.0.
