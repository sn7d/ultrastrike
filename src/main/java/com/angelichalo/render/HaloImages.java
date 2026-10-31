package com.angelichalo.render;

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

import com.angelichalo.AngelicHalo;

/**
 * Emblem images: built-in ones shipped in the jar, plus any PNG the player drops into
 * {@code config/angelichalo/images}. Everything is loaded locally.
 */
public final class HaloImages {
	private HaloImages() { }

	public static final List<String> BUILTIN = List.of("sigil", "sunburst", "compass", "lotus", "snowflake", "crescent");
	private static final long MAX_BYTES = 8L * 1024 * 1024;

	private record Loaded(Identifier id, long modified, int width, int height) { }

	private static final Map<String, Loaded> USER = new HashMap<>();
	private static final Map<String, String> FAILED = new HashMap<>();
	private static List<String> cachedList = List.of();
	private static long listTime;

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("angelichalo").resolve("images");
	}

	/** "builtin:x" and "file:y.png" keys, refreshed at most every two seconds. */
	public static List<String> available() {
		long now = System.currentTimeMillis();
		if (now - listTime < 2000 && !cachedList.isEmpty()) return cachedList;
		listTime = now;

		List<String> out = new ArrayList<>();
		for (String b : BUILTIN) out.add("builtin:" + b);

		Path dir = folder();
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

	public static String displayName(String key) {
		int colon = key.indexOf(':');
		String name = colon >= 0 ? key.substring(colon + 1) : key;
		if (name.toLowerCase(Locale.ROOT).endsWith(".png")) name = name.substring(0, name.length() - 4);
		return name.isEmpty() ? key : Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	public static Identifier builtin(String name) {
		return Identifier.fromNamespaceAndPath(AngelicHalo.MOD_ID, "textures/emblem/" + name + ".png");
	}

	/** Error for the last attempt at loading this key, or null. */
	public static String error(String key) {
		return FAILED.get(key);
	}

	/** Texture for a key; falls back to the sigil when a file is missing or broken. */
	public static Identifier texture(String key) {
		if (key.startsWith("builtin:")) {
			String name = key.substring("builtin:".length());
			return BUILTIN.contains(name) ? builtin(name) : builtin("sigil");
		}

		if (key.startsWith("file:")) {
			Identifier id = loadUser(key.substring("file:".length()));
			if (id != null) return id;
		}

		return builtin("sigil");
	}

	private static Identifier loadUser(String fileName) {
		if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) return null;
		Path path = folder().resolve(fileName);

		try {
			if (!Files.isRegularFile(path)) {
				FAILED.put("file:" + fileName, "not found");
				return null;
			}

			long modified = Files.getLastModifiedTime(path).toMillis();
			Loaded existing = USER.get(fileName);
			if (existing != null && existing.modified() == modified) return existing.id();
			if (Files.size(path) > MAX_BYTES) {
				FAILED.put("file:" + fileName, "file larger than 8 MB");
				return null;
			}

			NativeImage image;
			try (InputStream in = Files.newInputStream(path)) {
				image = NativeImage.read(in);
			}

			Identifier id = Identifier.fromNamespaceAndPath(AngelicHalo.MOD_ID, "user/" + sanitize(fileName));
			Minecraft mc = Minecraft.getInstance();
			int w = image.getWidth();
			int h = image.getHeight();
			DynamicTexture texture = new DynamicTexture(() -> "Angelic Halo image " + fileName, image);
			mc.getTextureManager().register(id, texture);
			USER.put(fileName, new Loaded(id, modified, w, h));
			FAILED.remove("file:" + fileName);
			return id;
		} catch (IOException | RuntimeException e) {
			FAILED.put("file:" + fileName, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
			AngelicHalo.LOGGER.warn("Could not load emblem image {}", fileName, e);
			return null;
		}
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
