package com.angelichalo.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses nametag text with Minecraft-style formatting codes into glyphs. Pure Java (unit-testable).
 *
 * <ul>
 *   <li>{@code &0}–{@code &f}: fixed colours (overrides the colour effect for the following letters)</li>
 *   <li>{@code &#RRGGBB}: any hex colour</li>
 *   <li>{@code &l} bold, {@code &o} italic, {@code &n} underline, {@code &m} strikethrough, {@code &k} obfuscated</li>
 *   <li>{@code &r}: reset back to the colour effect and plain style</li>
 *   <li>{@code &&}: a literal ampersand</li>
 * </ul>
 */
public final class NametagText {
	private NametagText() { }

	/** One visible character. {@code rgb} is -1 when the colour effect should colour it. */
	public record Glyph(String text, int rgb, boolean bold, boolean italic, boolean underline, boolean strike, boolean obfuscated) { }

	private static final int[] CODES = {
			0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
	};

	public static List<Glyph> parse(String s) {
		List<Glyph> out = new ArrayList<>();
		int rgb = -1;
		boolean bold = false;
		boolean italic = false;
		boolean underline = false;
		boolean strike = false;
		boolean obf = false;
		int i = 0;

		while (i < s.length()) {
			int cp = s.codePointAt(i);
			int len = Character.charCount(cp);

			if (cp == '&' && i + 1 < s.length()) {
				char n = Character.toLowerCase(s.charAt(i + 1));

				if (n == '&') {
					out.add(new Glyph("&", rgb, bold, italic, underline, strike, obf));
					i += 2;
					continue;
				}

				if (n == '#' && i + 8 <= s.length()) {
					try {
						rgb = Integer.parseInt(s.substring(i + 2, i + 8), 16);
						i += 8;
						continue;
					} catch (NumberFormatException ignored) {
						// not a hex code: print as-is
					}
				}

				int hex = Character.digit(n, 16);
				if (hex >= 0) {
					rgb = CODES[hex];
					// like vanilla, a colour code resets formatting
					bold = italic = underline = strike = obf = false;
					i += 2;
					continue;
				}

				boolean code = true;
				switch (n) {
					case 'l' -> bold = true;
					case 'o' -> italic = true;
					case 'n' -> underline = true;
					case 'm' -> strike = true;
					case 'k' -> obf = true;
					case 'r' -> {
						rgb = -1;
						bold = italic = underline = strike = obf = false;
					}
					default -> code = false;
				}

				if (code) {
					i += 2;
					continue;
				}
			}

			out.add(new Glyph(new String(Character.toChars(cp)), rgb, bold, italic, underline, strike, obf));
			i += len;
		}

		return out;
	}

	/** The text without formatting codes. */
	public static String plain(String s) {
		StringBuilder sb = new StringBuilder();
		for (Glyph g : parse(s)) sb.append(g.text());
		return sb.toString();
	}
}
