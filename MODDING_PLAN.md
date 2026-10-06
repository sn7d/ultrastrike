# Ultrastrike Modding Plan

- Install: Counter-Strike 2 and ULTRAKILL are reported installed on the user's Windows PC; their folders are not visible in this remote Linux container.
- Engine: CS2 uses Source 2. ULTRAKILL uses Unity; exact Unity version and scripting backend are unverified here.
- Anti-cheat / online: CS2 is VAC-protected online. Do not modify or inject into its client. The planned standalone reads local files only and is for offline play.
- Community route: No CS2 loader is installed by Melty. Use a standalone executable. ValveResourceFormat / Source 2 Viewer can export CS2 map resources to glTF; its CLI is available for Windows x64. Do not ship extracted game assets.
- Chosen route: standalone arena launched by Melty with both game folders passed in. Read and convert CS2 maps and any approved ULTRAKILL data on the user's machine; store generated files under the app's private data directory.
- Lab plan: keep all tests offline and outside the game processes. Use synthetic fixtures in this container. Validate actual conversions only against the user's Windows installs; do not edit either game folder.
- Unknowns to resolve first: mounted Windows install paths; CS2 map VPK filenames and collision quality; ULTRAKILL data files suitable for a read-only converter; target Windows runtime/export path.
- Publishing: Melty marks CS2 and ULTRAKILL for review. Keep the listing as a draft until one-click checks, real install testing, gameplay media, credits, and license/remix choices are settled.