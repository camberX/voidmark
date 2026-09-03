package dev.voidmark.client.ui;

import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Installed TrueType families on this machine, mapped to a regular-weight file
 * Minecraft's TTF provider can load.
 */
public final class SystemFonts {
	private static final Pattern SKIP = Pattern.compile(
		"(?i)(emoji|symbol|wingding|webding|marlett|awesome|material icons|noto color|notocoloremoji|seguiemj|segmdl2)"
	);
	private static final Object LOCK = new Object();
	private static Map<String, Path> cache;

	private SystemFonts() {
	}

	public static List<String> families() {
		return new ArrayList<>(index().keySet());
	}

	public static Path file(String family) {
		if (family == null || family.isBlank()) {
			return null;
		}
		return index().get(family);
	}

	private static Map<String, Path> index() {
		synchronized (LOCK) {
			if (cache != null) {
				return cache;
			}
			Map<String, Candidate> found = new LinkedHashMap<>();
			for (Path dir : roots()) {
				if (dir == null || !Files.isDirectory(dir)) {
					continue;
				}
				try (var walk = Files.walk(dir, 8)) {
					walk.filter(Files::isRegularFile).forEach(path -> consider(found, path));
				} catch (IOException ignored) {
				}
			}
			Map<String, Path> out = new LinkedHashMap<>();
			found.values().stream()
				.sorted(Comparator.comparing(candidate -> candidate.family.toLowerCase(Locale.ROOT)))
				.forEach(candidate -> out.put(candidate.family, candidate.path));
			cache = out;
			return cache;
		}
	}

	private static void consider(Map<String, Candidate> found, Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!(name.endsWith(".ttf") || name.endsWith(".otf"))) {
			return;
		}
		if (!hasGlyf(path)) {
			return;
		}
		String family = familyName(path);
		if (family == null || family.isBlank() || SKIP.matcher(family).find()) {
			return;
		}
		int rank = rank(name);
		Candidate current = found.get(family);
		if (current == null || rank < current.rank || (rank == current.rank && name.length() < current.path.getFileName().toString().length())) {
			found.put(family, new Candidate(family, path, rank));
		}
	}

	private static List<Path> roots() {
		List<Path> out = new ArrayList<>();
		String windir = System.getenv("WINDIR");
		if (windir != null && !windir.isBlank()) {
			out.add(Paths.get(windir, "Fonts"));
		}
		String local = System.getenv("LOCALAPPDATA");
		if (local != null && !local.isBlank()) {
			out.add(Paths.get(local, "Microsoft", "Windows", "Fonts"));
		}
		String home = System.getProperty("user.home");
		if (home != null && !home.isBlank()) {
			Path user = Paths.get(home);
			out.add(user.resolve("AppData/Local/Microsoft/Windows/Fonts"));
			out.add(user.resolve("Library/Fonts"));
			out.add(user.resolve(".local/share/fonts"));
			out.add(user.resolve(".fonts"));
		}
		out.add(Paths.get("/System/Library/Fonts"));
		out.add(Paths.get("/System/Library/Fonts/Supplemental"));
		out.add(Paths.get("/Library/Fonts"));
		out.add(Paths.get("/usr/share/fonts"));
		out.add(Paths.get("/usr/local/share/fonts"));
		return out;
	}

	private static boolean hasGlyf(Path path) {
		try (SeekableByteChannel channel = Files.newByteChannel(path)) {
			if (channel.size() < 12) {
				return false;
			}
			ByteBuffer head = read(channel, 0, 12);
			int tag = head.getInt(0);
			if (tag == 0x74746366) {
				return false;
			}
			int tables = head.getShort(4) & 0xffff;
			if (tables <= 0 || tables > 64) {
				return false;
			}
			ByteBuffer directory = read(channel, 12, tables * 16);
			for (int i = 0; i < tables; i++) {
				if (directory.getInt(i * 16) == 0x676c7966) {
					return true;
				}
			}
			return false;
		} catch (IOException ignored) {
			return false;
		}
	}

	private static String familyName(Path path) {
		String fromTable = nameTableFamily(path);
		if (fromTable != null && !fromTable.isBlank()) {
			return fromTable;
		}
		try {
			if (Files.size(path) > 2_000_000L) {
				return null;
			}
			try (InputStream in = Files.newInputStream(path)) {
				Font font = Font.createFont(Font.TRUETYPE_FONT, in);
				String family = font.getFamily(Locale.ROOT);
				return family == null || family.isBlank() ? null : family.trim();
			}
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String nameTableFamily(Path path) {
		try (SeekableByteChannel channel = Files.newByteChannel(path)) {
			ByteBuffer head = read(channel, 0, 12);
			int tables = head.getShort(4) & 0xffff;
			if (tables <= 0 || tables > 64) {
				return null;
			}
			ByteBuffer directory = read(channel, 12, tables * 16);
			int offset = -1;
			int length = 0;
			for (int i = 0; i < tables; i++) {
				if (directory.getInt(i * 16) == 0x6e616d65) {
					offset = directory.getInt(i * 16 + 8);
					length = directory.getInt(i * 16 + 12);
					break;
				}
			}
			if (offset < 0 || length < 6 || length > 1_000_000) {
				return null;
			}
			ByteBuffer name = read(channel, offset, length);
			int count = name.getShort(2) & 0xffff;
			int storage = name.getShort(4) & 0xffff;
			String family = null;
			String typographic = null;
			for (int i = 0; i < count; i++) {
				int row = 6 + i * 12;
				if (row + 12 > name.limit()) {
					break;
				}
				int platform = name.getShort(row) & 0xffff;
				int encoding = name.getShort(row + 2) & 0xffff;
				int nameId = name.getShort(row + 6) & 0xffff;
				int len = name.getShort(row + 8) & 0xffff;
				int stringOffset = name.getShort(row + 10) & 0xffff;
				if (nameId != 1 && nameId != 16) {
					continue;
				}
				if (platform != 0 && platform != 3) {
					continue;
				}
				int start = storage + stringOffset;
				if (start < 0 || start + len > name.limit()) {
					continue;
				}
				byte[] bytes = new byte[len];
				name.position(start);
				name.get(bytes);
				String value = new String(bytes, StandardCharsets.UTF_16BE).trim();
				if (value.isBlank() || encoding == 99) {
					continue;
				}
				if (nameId == 16) {
					typographic = value;
				} else if (family == null) {
					family = value;
				}
			}
			return typographic != null ? typographic : family;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static ByteBuffer read(SeekableByteChannel channel, long position, int length) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN);
		channel.position(position);
		while (buffer.hasRemaining()) {
			if (channel.read(buffer) < 0) {
				break;
			}
		}
		buffer.flip();
		return buffer;
	}

	private static int rank(String filename) {
		if (filename.contains("italic") || filename.contains("oblique") || filename.contains("slant")) {
			return 80;
		}
		if (filename.contains("regular") || filename.contains("roman")) {
			return 0;
		}
		if (filename.contains("book") || filename.contains("normal")) {
			return 1;
		}
		if (filename.contains("medium")) {
			return 2;
		}
		if (filename.contains("semibold") || filename.contains("demibold")) {
			return 12;
		}
		if (filename.contains("bold") || filename.contains("black") || filename.contains("heavy")) {
			return 40;
		}
		if (filename.contains("light") || filename.contains("thin") || filename.contains("hair")) {
			return 20;
		}
		return 5;
	}

	private record Candidate(String family, Path path, int rank) {
	}
}
