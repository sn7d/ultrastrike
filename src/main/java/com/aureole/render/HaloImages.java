package com.aureole.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import net.fabricmc.loader.api.FabricLoader;

import com.aureole.Aureole;

/**
 * Image libraries: built-in images shipped in the jar plus any PNG the player drops into a config folder.
 * Two libraries exist: emblems ({@code config/aureole/images}) and nametag icons ({@code config/aureole/icons}).
 * Everything is loaded locally; file checks are throttled so nothing touches the disk every frame.
 */
public final class HaloImages {
	public static final HaloImages EMBLEMS = new HaloImages("images", "emblem",
			List.of("sigil", "sunburst", "compass", "lotus", "snowflake", "crescent"));
	public static final HaloImages ICONS = new HaloImages("icons", "icon",
			List.of("crown", "star", "heart", "sword", "shield", "gem", "flame", "moon", "bolt", "sparkle", "leaf", "skull"));

	/** Built-in emblem names (kept for compatibility). */
	public static final List<String> BUILTIN = EMBLEMS.builtins;

	private static final long MAX_BYTES = 8L * 1024 * 1024;
	private static final long RECHECK_MS = 1500;

	private record Loaded(Identifier id, long modified, long checkedAt) { }

	private final String folderName;
	private final String textureDir;
	private final List<String> builtins;
	private final Map<String, Loaded> user = new HashMap<>();
	private final Map<String, String> failed = new HashMap<>();
	private final Map<String, Long> failedAt = new HashMap<>();
	private List<String> cachedList = List.of();
	private long listTime;

	private HaloImages(String folderName, String textureDir, List<String> builtins) {
		this.folderName = folderName;
		this.textureDir = textureDir;
		this.builtins = builtins;
	}

	public Path folderPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("aureole").resolve(folderName);
	}

	/** "builtin:x" and "file:y.png" keys, refreshed at most every two seconds. */
	public List<String> list() {
		long now = System.currentTimeMillis();
		if (now - listTime < 2000 && !cachedList.isEmpty()) return cachedList;
		listTime = now;

		List<String> out = new ArrayList<>();
		for (String b : builtins) out.add("builtin:" + b);

		Path dir = folderPath();
		try {
			Files.createDirectories(dir);
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{png,PNG}")) {
				List<String> files = new ArrayList<>();
				for (Path p : stream) files.add("file:" + p.getFileName());
				files.sort(String.CASE_INSENSITIVE_ORDER);
				out.addAll(files);
			}
		} catch (IOException e) {
			// folder unavailable: built-ins only
		}

		cachedList = List.copyOf(out);
		return cachedList;
	}

	public Identifier builtinId(String name) {
		return Identifier.fromNamespaceAndPath(Aureole.MOD_ID, "textures/" + textureDir + "/" + name + ".png");
	}

	/** Error for the last attempt at loading this key, or null. */
	public String errorFor(String key) {
		return failed.get(key);
	}

	/** Texture for a key; falls back to the first built-in when a file is missing or broken. */
	public Identifier textureFor(String key) {
		if (key.startsWith("builtin:")) {
			String name = key.substring("builtin:".length());
			return builtinId(builtins.contains(name) ? name : builtins.get(0));
		}

		if (key.startsWith("file:")) {
			Identifier id = loadUser(key.substring("file:".length()));
			if (id != null) return id;
		}

		return builtinId(builtins.get(0));
	}

	private Identifier loadUser(String fileName) {
		if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) return null;
		long now = System.currentTimeMillis();
		Loaded existing = user.get(fileName);

		// throttle disk checks: reuse what we have for a moment
		if (existing != null && now - existing.checkedAt() < RECHECK_MS) return existing.id();
		Long lastFail = failedAt.get(fileName);
		if (existing == null && lastFail != null && now - lastFail < RECHECK_MS) return null;

		Path path = folderPath().resolve(fileName);
		String key = "file:" + fileName;

		try {
			if (!Files.isRegularFile(path)) {
				fail(fileName, key, "not found");
				return null;
			}

			long modified = Files.getLastModifiedTime(path).toMillis();
			if (existing != null && existing.modified() == modified) {
				user.put(fileName, new Loaded(existing.id(), modified, now));
				return existing.id();
			}
			if (Files.size(path) > MAX_BYTES) {
				fail(fileName, key, "file larger than 8 MB");
				return null;
			}

			NativeImage image;
			try (InputStream in = Files.newInputStream(path)) {
				image = NativeImage.read(in);
			}

			Identifier id = Identifier.fromNamespaceAndPath(Aureole.MOD_ID, "user/" + textureDir + "/" + sanitize(fileName));
			DynamicTexture texture = new DynamicTexture(() -> "Aureole image " + fileName, image);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			user.put(fileName, new Loaded(id, modified, now));
			failed.remove(key);
			failedAt.remove(fileName);
			return id;
		} catch (IOException | RuntimeException e) {
			fail(fileName, key, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			Aureole.LOGGER.warn("Could not load image {}", fileName, e);
			return null;
		}
	}

	private void fail(String fileName, String key, String why) {
		failed.put(key, why);
		failedAt.put(fileName, System.currentTimeMillis());
	}

	// ---- emblem facade (existing call sites) ----

	public static List<String> available() {
		return EMBLEMS.list();
	}

	public static Identifier texture(String key) {
		return EMBLEMS.textureFor(key);
	}

	public static String error(String key) {
		return EMBLEMS.errorFor(key);
	}

	public static Path folder() {
		return EMBLEMS.folderPath();
	}

	public static String displayName(String key) {
		int colon = key.indexOf(':');
		String name = colon >= 0 ? key.substring(colon + 1) : key;
		if (name.toLowerCase(Locale.ROOT).endsWith(".png")) name = name.substring(0, name.length() - 4);
		return name.isEmpty() ? key : Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static String sanitize(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder();
		for (char ch : lower.toCharArray()) {
			sb.append((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '.' ? ch : '_');
		}
		return sb.toString();
	}
}
