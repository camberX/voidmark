package dev.voidmark.client.mining;

import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hypixel {@code [Area] Titanium} commissions only count ore inside that
 * named Dwarven Mines region. Markers are SkyHanni island-graph nodes with
 * {@code area} / {@code small_area} tags from {@code DWARVEN_MINES.json}.
 * A block belongs to the nearest marker within {@link #MAX_DIST}; other
 * named rooms (Forge, Village, Palace, {@code no_area} corridors) sit as
 * blockers so an adjacent job cannot claim them.
 */
public final class MiningAreas {
	private static final int MAX_DIST = 32;
	private static final int MAX_DIST_SQ = MAX_DIST * MAX_DIST;

	private MiningAreas() {
	}

	public enum Region {
		LAVA_SPRINGS("Lava Springs", "lava springs"),
		CLIFFSIDE_VEINS("Cliffside Veins", "cliffside"),
		RAMPARTS_QUARRY("Rampart's Quarry", "rampart"),
		UPPER_MINES("Upper Mines", "upper mines"),
		ROYAL_MINES("Royal Mines", "royal mines"),
		FAR_RESERVE("Far Reserve", "far reserve"),
		GOBLIN_BURROWS("Goblin Burrows", "goblin burrow"),
		GREAT_ICE_WALL("Great Ice Wall", "ice wall"),
		DWARVEN_VILLAGE("Dwarven Village", "dwarven village"),
		THE_MIST("The Mist", "mist"),
		FORGE_BASIN("Forge Basin", "forge basin");

		private final String label;
		private final String token;

		Region(String label, String token) {
			this.label = label;
			this.token = token;
		}

		public String label() {
			return label;
		}
	}

	public static Region of(int x, int y, int z) {
		Marker best = null;
		long bestDist = Long.MAX_VALUE;
		for (Marker marker : MARKERS) {
			long dist = marker.distSq(x, y, z);
			if (dist < bestDist) {
				bestDist = dist;
				best = marker;
			}
		}
		if (best == null || bestDist > MAX_DIST_SQ) {
			return null;
		}
		return best.region;
	}

	public static TitaniumFilter filter(List<MiningTracker.Commission> commissions) {
		if (commissions == null || commissions.isEmpty()) {
			return TitaniumFilter.NONE;
		}
		EnumSet<Region> areas = EnumSet.noneOf(Region.class);
		boolean unrestricted = false;
		boolean any = false;
		for (MiningTracker.Commission commission : commissions) {
			if (commission == null || commission.done()) {
				continue;
			}
			String key = normalize(commission.name());
			if (key.isEmpty() || !key.contains("titanium")) {
				continue;
			}
			any = true;
			if (genericMiner(key)) {
				unrestricted = true;
				continue;
			}
			Region region = match(key);
			if (region != null) {
				areas.add(region);
			}
		}
		if (!any) {
			return TitaniumFilter.NONE;
		}
		if (unrestricted) {
			return TitaniumFilter.ALL;
		}
		if (areas.isEmpty()) {
			return TitaniumFilter.NONE;
		}
		return TitaniumFilter.areas(areas);
	}

	private static boolean genericMiner(String key) {
		return key.equals("titanium") || key.contains("titanium miner");
	}

	private static Region match(String key) {
		Region best = null;
		int bestLen = 0;
		for (Region region : Region.values()) {
			if (key.contains(region.token) && region.token.length() > bestLen) {
				best = region;
				bestLen = region.token.length();
			}
		}
		return best;
	}

	private static String normalize(String name) {
		if (name == null) {
			return "";
		}
		String text = name.toLowerCase(Locale.ROOT);
		text = text.replace('’', '\'').replace('`', '\'');
		return text.replaceAll("[^a-z0-9]+", " ").trim();
	}

	public record TitaniumFilter(boolean active, boolean unrestricted, Set<Region> regions) {
		public static final TitaniumFilter NONE = new TitaniumFilter(false, false, Set.of());
		public static final TitaniumFilter ALL = new TitaniumFilter(true, true, Set.of());

		private static TitaniumFilter areas(Set<Region> regions) {
			return new TitaniumFilter(true, false, Set.copyOf(regions));
		}

		public boolean allows(int x, int y, int z) {
			if (!active) {
				return false;
			}
			if (unrestricted) {
				return true;
			}
			Region region = of(x, y, z);
			return region != null && regions.contains(region);
		}

		public boolean allows(BlockPos pos) {
			return pos != null && allows(pos.getX(), pos.getY(), pos.getZ());
		}

		public String label() {
			if (!active) {
				return "";
			}
			if (unrestricted) {
				return "all";
			}
			return regions.stream().map(Region::label).collect(Collectors.joining(" + "));
		}
	}

	private record Marker(Region region, int x, int y, int z) {
		long distSq(int bx, int by, int bz) {
			long dx = bx - x;
			long dy = by - y;
			long dz = bz - z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	private static Marker m(Region region, int x, int y, int z) {
		return new Marker(region, x, y, z);
	}

	private static final Marker[] MARKERS = {
		m(Region.LAVA_SPRINGS, 8, 191, 2),
		m(Region.LAVA_SPRINGS, 28, 192, 6),
		m(Region.LAVA_SPRINGS, 30, 195, -9),
		m(Region.LAVA_SPRINGS, 82, 195, -20),
		m(Region.CLIFFSIDE_VEINS, -33, 132, 36),
		m(Region.CLIFFSIDE_VEINS, -30, 132, 33),
		m(Region.CLIFFSIDE_VEINS, -18, 129, 58),
		m(Region.CLIFFSIDE_VEINS, -4, 129, 60),
		m(Region.CLIFFSIDE_VEINS, 0, 129, 47),
		m(Region.CLIFFSIDE_VEINS, 4, 129, 60),
		m(Region.CLIFFSIDE_VEINS, 32, 140, 7),
		m(Region.CLIFFSIDE_VEINS, 33, 135, 22),
		m(Region.CLIFFSIDE_VEINS, 37, 129, 48),
		m(Region.CLIFFSIDE_VEINS, 85, 139, 50),
		m(Region.CLIFFSIDE_VEINS, 97, 150, 40),
		m(Region.RAMPARTS_QUARRY, -124, 178, -54),
		m(Region.RAMPARTS_QUARRY, -124, 201, -35),
		m(Region.RAMPARTS_QUARRY, -123, 203, -44),
		m(Region.RAMPARTS_QUARRY, -122, 201, -27),
		m(Region.RAMPARTS_QUARRY, -122, 206, -55),
		m(Region.RAMPARTS_QUARRY, -122, 217, -29),
		m(Region.RAMPARTS_QUARRY, -121, 205, -49),
		m(Region.RAMPARTS_QUARRY, -121, 207, -61),
		m(Region.RAMPARTS_QUARRY, -121, 217, -24),
		m(Region.RAMPARTS_QUARRY, -120, 149, 16),
		m(Region.RAMPARTS_QUARRY, -120, 204, -20),
		m(Region.RAMPARTS_QUARRY, -113, 150, 19),
		m(Region.RAMPARTS_QUARRY, -110, 168, -68),
		m(Region.RAMPARTS_QUARRY, -109, 187, -62),
		m(Region.RAMPARTS_QUARRY, -108, 222, -60),
		m(Region.RAMPARTS_QUARRY, -104, 187, -61),
		m(Region.RAMPARTS_QUARRY, -100, 207, -68),
		m(Region.RAMPARTS_QUARRY, -98, 217, -41),
		m(Region.RAMPARTS_QUARRY, -97, 177, 30),
		m(Region.RAMPARTS_QUARRY, -97, 192, -39),
		m(Region.RAMPARTS_QUARRY, -97, 222, -60),
		m(Region.RAMPARTS_QUARRY, -95, 160, 23),
		m(Region.RAMPARTS_QUARRY, -94, 188, -61),
		m(Region.RAMPARTS_QUARRY, -94, 222, -41),
		m(Region.RAMPARTS_QUARRY, -93, 222, -60),
		m(Region.RAMPARTS_QUARRY, -91, 197, 24),
		m(Region.RAMPARTS_QUARRY, -82, 188, -63),
		m(Region.RAMPARTS_QUARRY, -80, 195, 25),
		m(Region.RAMPARTS_QUARRY, -74, 207, -62),
		m(Region.RAMPARTS_QUARRY, -69, 208, -41),
		m(Region.RAMPARTS_QUARRY, -65, 207, -62),
		m(Region.RAMPARTS_QUARRY, -63, 208, -70),
		m(Region.RAMPARTS_QUARRY, -63, 208, -41),
		m(Region.RAMPARTS_QUARRY, -61, 163, -35),
		m(Region.RAMPARTS_QUARRY, -55, 139, 16),
		m(Region.RAMPARTS_QUARRY, -53, 213, -55),
		m(Region.RAMPARTS_QUARRY, -52, 139, -5),
		m(Region.RAMPARTS_QUARRY, -50, 166, -34),
		m(Region.RAMPARTS_QUARRY, -35, 133, 29),
		m(Region.RAMPARTS_QUARRY, -32, 168, -28),
		m(Region.RAMPARTS_QUARRY, -31, 143, -21),
		m(Region.RAMPARTS_QUARRY, -30, 146, 0),
		m(Region.RAMPARTS_QUARRY, -30, 176, -32),
		m(Region.RAMPARTS_QUARRY, -28, 175, -55),
		m(Region.RAMPARTS_QUARRY, -27, 143, -15),
		m(Region.UPPER_MINES, -155, 165, -13),
		m(Region.UPPER_MINES, -149, 166, -8),
		m(Region.UPPER_MINES, -135, 174, -61),
		m(Region.UPPER_MINES, -129, 175, -56),
		m(Region.UPPER_MINES, -129, 175, -52),
		m(Region.UPPER_MINES, -129, 202, -34),
		m(Region.UPPER_MINES, -128, 203, -43),
		m(Region.UPPER_MINES, -127, 176, -48),
		m(Region.UPPER_MINES, -127, 202, -37),
		m(Region.UPPER_MINES, -127, 203, -26),
		m(Region.UPPER_MINES, -127, 206, -54),
		m(Region.UPPER_MINES, -126, 217, -20),
		m(Region.UPPER_MINES, -114, 170, -71),
		m(Region.UPPER_MINES, -110, 168, -71),
		m(Region.UPPER_MINES, -104, 222, -67),
		m(Region.UPPER_MINES, -101, 187, -67),
		m(Region.UPPER_MINES, -97, 222, -67),
		m(Region.UPPER_MINES, -96, 188, -67),
		m(Region.UPPER_MINES, -94, 222, -69),
		m(Region.UPPER_MINES, -92, 203, -72),
		m(Region.UPPER_MINES, -88, 188, -68),
		m(Region.UPPER_MINES, -83, 188, -69),
		m(Region.UPPER_MINES, -69, 207, -67),
		m(Region.UPPER_MINES, -68, 211, -84),
		m(Region.UPPER_MINES, -63, 208, -73),
		m(Region.ROYAL_MINES, 92, 110, 116),
		m(Region.ROYAL_MINES, 94, 117, 132),
		m(Region.ROYAL_MINES, 100, 152, 40),
		m(Region.ROYAL_MINES, 101, 132, 135),
		m(Region.ROYAL_MINES, 107, 103, 154),
		m(Region.ROYAL_MINES, 108, 103, 97),
		m(Region.ROYAL_MINES, 114, 168, 29),
		m(Region.ROYAL_MINES, 118, 146, 61),
		m(Region.ROYAL_MINES, 118, 150, 97),
		m(Region.ROYAL_MINES, 120, 168, 29),
		m(Region.ROYAL_MINES, 125, 134, 51),
		m(Region.ROYAL_MINES, 129, 150, 98),
		m(Region.ROYAL_MINES, 136, 128, 78),
		m(Region.ROYAL_MINES, 146, 162, 97),
		m(Region.ROYAL_MINES, 148, 110, 116),
		m(Region.ROYAL_MINES, 156, 151, 39),
		m(Region.FAR_RESERVE, -157, 150, 5),
		m(Region.FAR_RESERVE, -156, 161, 100),
		m(Region.FAR_RESERVE, -150, 150, 5),
		m(Region.FAR_RESERVE, -141, 152, 89),
		m(Region.FAR_RESERVE, -132, 150, 17),
		m(Region.FAR_RESERVE, -127, 165, 131),
		m(Region.FAR_RESERVE, -126, 143, 114),
		m(Region.FAR_RESERVE, -123, 177, 61),
		m(Region.FAR_RESERVE, -112, 172, 60),
		m(Region.FAR_RESERVE, -105, 143, 126),
		m(Region.FAR_RESERVE, -103, 151, 77),
		m(Region.FAR_RESERVE, -98, 143, 108),
		m(Region.FAR_RESERVE, -98, 143, 114),
		m(Region.FAR_RESERVE, -95, 160, 114),
		m(Region.GOBLIN_BURROWS, -159, 160, 101),
		m(Region.GOBLIN_BURROWS, -144, 152, 91),
		m(Region.GOBLIN_BURROWS, -131, 143, 116),
		m(Region.GOBLIN_BURROWS, -124, 166, 136),
		m(Region.GOBLIN_BURROWS, -106, 145, 134),
		m(Region.GOBLIN_BURROWS, -88, 162, 120),
		m(Region.GOBLIN_BURROWS, -57, 136, 155),
		m(Region.GREAT_ICE_WALL, -29, 129, 159),
		m(Region.GREAT_ICE_WALL, -4, 129, 145),
		m(Region.GREAT_ICE_WALL, 4, 129, 145),
		m(Region.GREAT_ICE_WALL, 38, 131, 167),
		m(Region.GREAT_ICE_WALL, 67, 165, 135),
		m(Region.GREAT_ICE_WALL, 71, 141, 148),
		m(Region.GREAT_ICE_WALL, 71, 165, 132),
		m(Region.GREAT_ICE_WALL, 74, 165, 130),
		m(Region.GREAT_ICE_WALL, 75, 148, 151),
		m(Region.GREAT_ICE_WALL, 84, 152, 138),
		m(Region.DWARVEN_VILLAGE, -45, 201, -122),
		m(Region.DWARVEN_VILLAGE, -39, 224, -106),
		m(Region.DWARVEN_VILLAGE, -3, 203, -68),
		m(Region.DWARVEN_VILLAGE, 25, 203, -134),
		m(Region.DWARVEN_VILLAGE, 39, 202, -92),
		m(Region.DWARVEN_VILLAGE, 64, 201, -103),
		m(Region.THE_MIST, -19, 87, 62),
		m(Region.THE_MIST, 18, 85, 129),
		m(Region.THE_MIST, 39, 84, 54),
		m(Region.THE_MIST, 51, 93, 119),
		m(Region.THE_MIST, 54, 92, 54),
		m(Region.THE_MIST, 124, 83, 73),
		m(Region.THE_MIST, 128, 83, 59),
		m(Region.FORGE_BASIN, -23, 145, -25),
		m(Region.FORGE_BASIN, -21, 145, -17),
		m(Region.FORGE_BASIN, -16, 146, 5),
		m(Region.FORGE_BASIN, -6, 146, -22),
		m(Region.FORGE_BASIN, -4, 148, -32),
		m(Region.FORGE_BASIN, 0, 146, 24),
		m(Region.FORGE_BASIN, 4, 148, -32),
		m(Region.FORGE_BASIN, 17, 145, -4),
		m(Region.FORGE_BASIN, 18, 164, -26),
		m(Region.FORGE_BASIN, 25, 172, -13),
		m(Region.FORGE_BASIN, 26, 144, -1),
		m(null, -184, 162, 1),
		m(null, -183, 163, 4),
		m(null, -182, 162, -37),
		m(null, -179, 160, -39),
		m(null, -162, 179, 39),
		m(null, -161, 177, 60),
		m(null, -160, 208, 87),
		m(null, -157, 152, 3),
		m(null, -157, 164, -9),
		m(null, -150, 165, -4),
		m(null, -146, 150, -30),
		m(null, -146, 150, -28),
		m(null, -137, 174, -61),
		m(null, -137, 179, 39),
		m(null, -135, 179, 44),
		m(null, -128, 150, 17),
		m(null, -125, 178, 64),
		m(null, -124, 150, 17),
		m(null, -124, 215, -30),
		m(null, -123, 207, -62),
		m(null, -121, 178, 64),
		m(null, -109, 176, 27),
		m(null, -108, 177, 60),
		m(null, -106, 144, 130),
		m(null, -104, 176, 70),
		m(null, -100, 178, 30),
		m(null, -100, 208, 88),
		m(null, -97, 177, 20),
		m(null, -94, 202, -34),
		m(null, -93, 143, 108),
		m(null, -93, 143, 115),
		m(null, -92, 195, 20),
		m(null, -80, 195, 22),
		m(null, -73, 201, -122),
		m(null, -70, 201, -122),
		m(null, -69, 208, -27),
		m(null, -68, 216, -95),
		m(null, -68, 222, -106),
		m(null, -63, 208, -27),
		m(null, -61, 207, -15),
		m(null, -61, 207, 17),
		m(null, -60, 206, -41),
		m(null, -60, 206, -27),
		m(null, -59, 163, -33),
		m(null, -52, 135, 156),
		m(null, -50, 164, -32),
		m(null, -49, 211, -55),
		m(null, -47, 201, -122),
		m(null, -43, 222, -119),
		m(null, -43, 222, -109),
		m(null, -33, 131, 159),
		m(null, -30, 161, -25),
		m(null, -30, 161, 9),
		m(null, -28, 163, -30),
		m(null, -27, 182, -31),
		m(null, -27, 208, -56),
		m(null, -27, 208, -50),
		m(null, -26, 163, -27),
		m(null, -25, 182, -27),
		m(null, -25, 199, 41),
		m(null, -24, 161, 7),
		m(null, -24, 183, -31),
		m(null, -23, 176, -55),
		m(null, -19, 98, 63),
		m(null, -18, 126, 61),
		m(null, -18, 178, -55),
		m(null, -15, 208, -59),
		m(null, -13, 179, -55),
		m(null, -5, 181, -27),
		m(null, -5, 183, -30),
		m(null, -4, 129, 63),
		m(null, -4, 129, 136),
		m(null, -4, 148, -36),
		m(null, -3, 173, 43),
		m(null, -2, 147, 28),
		m(null, 0, 149, -34),
		m(null, 2, 147, 28),
		m(null, 4, 129, 63),
		m(null, 4, 129, 136),
		m(null, 4, 148, -36),
		m(null, 4, 182, 18),
		m(null, 5, 186, 2),
		m(null, 16, 128, 132),
		m(null, 17, 123, 129),
		m(null, 18, 98, 129),
		m(null, 18, 182, -28),
		m(null, 18, 183, -30),
		m(null, 21, 173, 8),
		m(null, 22, 174, 13),
		m(null, 25, 182, -12),
		m(null, 26, 203, -139),
		m(null, 28, 193, -10),
		m(null, 29, 142, 3),
		m(null, 32, 159, 17),
		m(null, 38, 119, 53),
		m(null, 38, 126, 51),
		m(null, 39, 93, 54),
		m(null, 39, 202, -89),
		m(null, 40, 190, -55),
		m(null, 42, 132, 171),
		m(null, 42, 191, -55),
		m(null, 56, 94, 53),
		m(null, 56, 158, 122),
		m(null, 64, 172, 132),
		m(null, 67, 158, 19),
		m(null, 68, 199, -97),
		m(null, 70, 176, 127),
		m(null, 70, 188, 122),
		m(null, 71, 188, 116),
		m(null, 71, 200, -102),
		m(null, 74, 187, 153),
		m(null, 74, 200, -105),
		m(null, 75, 141, 148),
		m(null, 75, 188, 113),
		m(null, 76, 187, 129),
		m(null, 78, 189, 129),
		m(null, 78, 189, 153),
		m(null, 81, 188, 129),
		m(null, 82, 136, 50),
		m(null, 85, 188, 160),
		m(null, 86, 188, 56),
		m(null, 86, 189, 54),
		m(null, 86, 195, -22),
		m(null, 88, 110, 116),
		m(null, 88, 188, 160),
		m(null, 89, 152, 138),
		m(null, 91, 117, 132),
		m(null, 96, 197, 157),
		m(null, 96, 197, 181),
		m(null, 97, 132, 137),
		m(null, 98, 195, -21),
		m(null, 99, 195, -24),
		m(null, 99, 197, 157),
		m(null, 99, 197, 181),
		m(null, 102, 195, -21),
		m(null, 107, 103, 92),
		m(null, 108, 197, 203),
		m(null, 108, 197, 208),
		m(null, 108, 197, 213),
		m(null, 110, 104, 159),
		m(null, 115, 168, 24),
		m(null, 115, 187, 17),
		m(null, 115, 187, 23),
		m(null, 120, 188, 97),
		m(null, 121, 141, 60),
		m(null, 122, 189, 97),
		m(null, 124, 122, 60),
		m(null, 127, 95, 59),
		m(null, 129, 152, 104),
		m(null, 129, 188, 107),
		m(null, 129, 188, 111),
		m(null, 129, 188, 115),
		m(null, 142, 187, 96),
		m(null, 143, 187, 99),
		m(null, 143, 188, 16),
		m(null, 145, 162, 100),
		m(null, 145, 187, 100),
		m(null, 149, 197, 201),
		m(null, 150, 110, 116),
		m(null, 150, 197, 213),
		m(null, 151, 197, 206),
		m(null, 156, 149, 41),
		m(null, 159, 197, 181),
		m(null, 163, 197, 181)
	};
}
