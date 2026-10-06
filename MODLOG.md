# Mod Log

## 2026-10-06

- User approved a standalone, solo-first arena requiring Counter-Strike 2 and ULTRAKILL.
- Core loop: free movement/weapon practice, clear enemy waves, enter an ULTRAKILL-style exit door, then load another CS2 map.
- First playable scope: two CS2 maps, ULTRAKILL-inspired movement, one weapon archetype from each game, and one enemy archetype.
- Do not inject into, patch, or launch CS2. Keep testing offline; never use VAC-secured servers.
- Melty catalog: CS2 is Source 2, has no auto-installed mod loader, and needs sales review. ULTRAKILL is Unity; Melty auto-installs BepInEx 5, but that loader is not part of this standalone route.
- Source 2 Viewer / ValveResourceFormat is MIT-licensed and documents map VPK import and glTF export. Include its required attribution if its CLI or library ships with the project.
- The workspace is a remote Ubuntu container. The user's Windows Steam folders are not mounted here; `um scan --list` found no installs. No game version, map VPK, or ULTRAKILL assets have been inspected locally.
- Toolkit checkout: `/tmp/universal-modder` (outside the project); .NET SDK 10 is available in the container.

## Current blockers

- Need access to the Windows CS2 and ULTRAKILL install folders to confirm versions, map VPK names, map collision export, and available ULTRAKILL data.
- Need a Windows test or capture path before calling the prototype playable in the real game context.
- Exact player tuning, weapon values, and enemy behavior are initial prototype decisions, not values verified from either game.

## Next

1. Mount the Steam library into the workspace or test on Windows with the local installs.
2. Run the MVP sheet preflight and resolve every game-file reference.
3. Implement and test one vertical slice before expanding content.