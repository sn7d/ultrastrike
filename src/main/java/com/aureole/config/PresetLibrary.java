package com.aureole.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import net.fabricmc.loader.api.FabricLoader;

/** Saved looks on disk ({@code config/aureole/presets/*.json}) plus clipboard share codes. */
public final class PresetLibrary {
	private PresetLibrary() { }

	public static final String CODE_PREFIX = "AU1:";
	/** Codes made before the rename (Angelic Halo) still paste fine. */
	private static final String LEGACY_PREFIX = "AH1:";
	private static final Gson GSON = new Gson();

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("aureole").resolve("presets");
	}

	public static List<String> list() {
		List<String> out = new ArrayList<>();
		try {
			Files.createDirectories(folder());
			try (DirectoryStream<Path> s = Files.newDirectoryStream(folder(), "*.json")) {
				for (Path p : s) {
					String n = p.getFileName().toString();
					out.add(n.substring(0, n.length() - 5));
				}
			}
		} catch (IOException ignored) {
			// empty library
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	public static String safeName(String name) {
		String t = name.trim().replaceAll("[^A-Za-z0-9 _\\-]", "").trim();
		if (t.length() > 32) t = t.substring(0, 32);
		return t;
	}

	public static boolean save(String name, HaloConfig config) {
		String n = safeName(name);
		if (n.isEmpty()) return false;
		try {
			Files.createDirectories(folder());
			Files.writeString(folder().resolve(n + ".json"), config.toJson(), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static HaloConfig load(String name) {
		try {
			String json = Files.readString(folder().resolve(safeName(name) + ".json"), StandardCharsets.UTF_8);
			HaloConfig c = GSON.fromJson(json, HaloConfig.class);
			if (c != null) c.sanitize();
			return c;
		} catch (IOException | JsonParseException e) {
			return null;
		}
	}

	public static boolean delete(String name) {
		try {
			return Files.deleteIfExists(folder().resolve(safeName(name) + ".json"));
		} catch (IOException e) {
			return false;
		}
	}

	public static String toCode(HaloConfig config) {
		return CODE_PREFIX + Base64.getEncoder().encodeToString(config.toJson().getBytes(StandardCharsets.UTF_8));
	}

	/** @return the decoded config, or null if the text isn't a valid share code */
	public static HaloConfig fromCode(String text) {
		if (text == null) return null;
		String t = text.trim();
		String upper = t.toUpperCase(Locale.ROOT);
		if (!upper.startsWith(CODE_PREFIX) && !upper.startsWith(LEGACY_PREFIX)) return null;
		try {
			String json = new String(Base64.getDecoder().decode(t.substring(CODE_PREFIX.length())), StandardCharsets.UTF_8);
			HaloConfig c = GSON.fromJson(json, HaloConfig.class);
			if (c != null) c.sanitize();
			return c;
		} catch (IllegalArgumentException | JsonParseException e) {
			return null;
		}
	}
}
