package dev.voidmark.client.mining;

import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hypixel {@code [Area] Titanium} commissions only count ore inside that
 * named Dwarven Mines region. Markers are SkyHanni island-graph area nodes
 * (plus emissary waypoints). A block belongs to the nearest marker within
 * {@link #MAX_DIST}; neighbouring zones are included as blockers so Far
 * Reserve / The Forge / the Village cannot be claimed by an adjacent job.
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
		ROYAL_MINES("Royal Mines", "royal mines");

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
		for (Region region : Region.values()) {
			if (key.contains(region.token)) {
				return region;
			}
		}
		return null;
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
		m(Region.LAVA_SPRINGS, 82, 195, -20),
		m(Region.LAVA_SPRINGS, 8, 191, 2),
		m(Region.LAVA_SPRINGS, 30, 195, -9),
		m(Region.LAVA_SPRINGS, 28, 192, 6),
		m(Region.LAVA_SPRINGS, 58, 198, -8),
		m(Region.LAVA_SPRINGS, 57, 196, -15),
		m(Region.LAVA_SPRINGS, 39, 204, -18),
		m(Region.LAVA_SPRINGS, 71, 197, -16),
		m(Region.LAVA_SPRINGS, 55, 196, -1),
		m(Region.CLIFFSIDE_VEINS, 97, 150, 40),
		m(Region.CLIFFSIDE_VEINS, 32, 140, 7),
		m(Region.CLIFFSIDE_VEINS, -30, 132, 33),
		m(Region.CLIFFSIDE_VEINS, -33, 132, 36),
		m(Region.CLIFFSIDE_VEINS, 0, 129, 47),
		m(Region.CLIFFSIDE_VEINS, 4, 129, 60),
		m(Region.CLIFFSIDE_VEINS, -4, 129, 60),
		m(Region.CLIFFSIDE_VEINS, 37, 129, 48),
		m(Region.CLIFFSIDE_VEINS, -18, 129, 58),
		m(Region.CLIFFSIDE_VEINS, 85, 139, 50),
		m(Region.CLIFFSIDE_VEINS, 33, 135, 22),
		m(Region.CLIFFSIDE_VEINS, 42, 134, 22),
		m(Region.CLIFFSIDE_VEINS, 40, 136, 17),
		m(Region.CLIFFSIDE_VEINS, 72, 145, 40),
		m(Region.RAMPARTS_QUARRY, -28, 175, -55),
		m(Region.RAMPARTS_QUARRY, -124, 178, -54),
		m(Region.RAMPARTS_QUARRY, -109, 187, -62),
		m(Region.RAMPARTS_QUARRY, -104, 187, -61),
		m(Region.RAMPARTS_QUARRY, -82, 188, -63),
		m(Region.RAMPARTS_QUARRY, -120, 149, 16),
		m(Region.RAMPARTS_QUARRY, -95, 160, 23),
		m(Region.RAMPARTS_QUARRY, -97, 177, 30),
		m(Region.RAMPARTS_QUARRY, -27, 143, -15),
		m(Region.RAMPARTS_QUARRY, -35, 133, 29),
		m(Region.RAMPARTS_QUARRY, -113, 150, 19),
		m(Region.RAMPARTS_QUARRY, -121, 217, -24),
		m(Region.RAMPARTS_QUARRY, -108, 222, -60),
		m(Region.RAMPARTS_QUARRY, -93, 222, -60),
		m(Region.RAMPARTS_QUARRY, -74, 207, -62),
		m(Region.RAMPARTS_QUARRY, -65, 207, -62),
		m(Region.RAMPARTS_QUARRY, -121, 205, -49),
		m(Region.RAMPARTS_QUARRY, -110, 168, -68),
		m(Region.RAMPARTS_QUARRY, -63, 208, -70),
		m(Region.RAMPARTS_QUARRY, -94, 188, -61),
		m(Region.RAMPARTS_QUARRY, -122, 201, -27),
		m(Region.RAMPARTS_QUARRY, -120, 204, -20),
		m(Region.RAMPARTS_QUARRY, -122, 206, -55),
		m(Region.RAMPARTS_QUARRY, -123, 203, -44),
		m(Region.RAMPARTS_QUARRY, -124, 201, -35),
		m(Region.RAMPARTS_QUARRY, -63, 208, -41),
		m(Region.RAMPARTS_QUARRY, -69, 208, -41),
		m(Region.RAMPARTS_QUARRY, -52, 139, -5),
		m(Region.RAMPARTS_QUARRY, -31, 143, -21),
		m(Region.RAMPARTS_QUARRY, -55, 139, 16),
		m(Region.RAMPARTS_QUARRY, -30, 176, -32),
		m(Region.RAMPARTS_QUARRY, -94, 222, -41),
		m(Region.RAMPARTS_QUARRY, -121, 207, -61),
		m(Region.RAMPARTS_QUARRY, -97, 192, -39),
		m(Region.RAMPARTS_QUARRY, -30, 146, 0),
		m(Region.RAMPARTS_QUARRY, -50, 166, -34),
		m(Region.RAMPARTS_QUARRY, -61, 163, -35),
		m(Region.RAMPARTS_QUARRY, -53, 213, -55),
		m(Region.RAMPARTS_QUARRY, -80, 195, 25),
		m(Region.RAMPARTS_QUARRY, -91, 197, 24),
		m(Region.RAMPARTS_QUARRY, -100, 207, -68),
		m(Region.RAMPARTS_QUARRY, -97, 222, -60),
		m(Region.RAMPARTS_QUARRY, -98, 217, -41),
		m(Region.RAMPARTS_QUARRY, -122, 217, -29),
		m(Region.RAMPARTS_QUARRY, -32, 168, -28),
		m(Region.RAMPARTS_QUARRY, -72, 153, -10),
		m(Region.RAMPARTS_QUARRY, -106, 147, 2),
		m(Region.RAMPARTS_QUARRY, -85, 148, -13),
		m(Region.UPPER_MINES, -101, 187, -67),
		m(Region.UPPER_MINES, -96, 188, -67),
		m(Region.UPPER_MINES, -92, 203, -72),
		m(Region.UPPER_MINES, -88, 188, -68),
		m(Region.UPPER_MINES, -135, 174, -61),
		m(Region.UPPER_MINES, -114, 170, -71),
		m(Region.UPPER_MINES, -155, 165, -13),
		m(Region.UPPER_MINES, -128, 203, -43),
		m(Region.UPPER_MINES, -126, 217, -20),
		m(Region.UPPER_MINES, -97, 222, -67),
		m(Region.UPPER_MINES, -104, 222, -67),
		m(Region.UPPER_MINES, -69, 207, -67),
		m(Region.UPPER_MINES, -127, 202, -37),
		m(Region.UPPER_MINES, -68, 211, -84),
		m(Region.UPPER_MINES, -127, 176, -48),
		m(Region.UPPER_MINES, -129, 175, -52),
		m(Region.UPPER_MINES, -129, 175, -56),
		m(Region.UPPER_MINES, -110, 168, -71),
		m(Region.UPPER_MINES, -63, 208, -73),
		m(Region.UPPER_MINES, -127, 203, -26),
		m(Region.UPPER_MINES, -127, 206, -54),
		m(Region.UPPER_MINES, -129, 202, -34),
		m(Region.UPPER_MINES, -83, 188, -69),
		m(Region.UPPER_MINES, -149, 166, -8),
		m(Region.UPPER_MINES, -94, 222, -69),
		m(Region.UPPER_MINES, -132, 174, -50),
		m(Region.UPPER_MINES, -123, 170, -71),
		m(Region.UPPER_MINES, -142, 213, -9),
		m(Region.ROYAL_MINES, 114, 168, 29),
		m(Region.ROYAL_MINES, 120, 168, 29),
		m(Region.ROYAL_MINES, 146, 162, 97),
		m(Region.ROYAL_MINES, 129, 150, 98),
		m(Region.ROYAL_MINES, 100, 152, 40),
		m(Region.ROYAL_MINES, 118, 150, 97),
		m(Region.ROYAL_MINES, 118, 146, 61),
		m(Region.ROYAL_MINES, 107, 103, 154),
		m(Region.ROYAL_MINES, 101, 132, 135),
		m(Region.ROYAL_MINES, 94, 117, 132),
		m(Region.ROYAL_MINES, 108, 103, 97),
		m(Region.ROYAL_MINES, 125, 134, 51),
		m(Region.ROYAL_MINES, 148, 110, 116),
		m(Region.ROYAL_MINES, 92, 110, 116),
		m(Region.ROYAL_MINES, 136, 128, 78),
		m(Region.ROYAL_MINES, 156, 151, 39),
		m(Region.ROYAL_MINES, 171, 150, 31),
		m(Region.ROYAL_MINES, 178, 149, 71),
		m(Region.ROYAL_MINES, 176, 152, 21),
		m(null, -184, 162, 1),
		m(null, -182, 162, -37),
		m(null, -146, 150, -28),
		m(null, 129, 152, 104),
		m(null, 88, 188, 160),
		m(null, 89, 152, 138),
		m(null, 96, 197, 181),
		m(null, 4, 129, 63),
		m(null, -4, 129, 63),
		m(null, -4, 129, 136),
		m(null, 4, 129, 136),
		m(null, 16, 128, 132),
		m(null, 26, 203, -139),
		m(null, 64, 201, -103),
		m(null, 25, 203, -134),
		m(null, 39, 202, -92),
		m(null, -39, 224, -106),
		m(null, -45, 201, -122),
		m(null, -3, 203, -68),
		m(null, -132, 150, 17),
		m(null, -157, 150, 5),
		m(null, -112, 172, 60),
		m(null, -123, 177, 61),
		m(null, -103, 151, 77),
		m(null, -141, 152, 89),
		m(null, -156, 161, 100),
		m(null, -95, 160, 114),
		m(null, -127, 165, 131),
		m(null, -126, 143, 114),
		m(null, -105, 143, 126),
		m(null, -98, 143, 108),
		m(null, -98, 143, 114),
		m(null, -150, 150, 5),
		m(null, 26, 144, -1),
		m(null, -16, 146, 5),
		m(null, -4, 148, -32),
		m(null, 4, 148, -32),
		m(null, 0, 146, 24),
		m(null, -21, 145, -17),
		m(null, -6, 146, -22),
		m(null, 17, 145, -4),
		m(null, 18, 164, -26),
		m(null, -23, 145, -25),
		m(null, 25, 172, -13),
		m(null, -13, 179, -55),
		m(null, 40, 190, -55),
		m(null, -24, 183, -31),
		m(null, 18, 183, -30),
		m(null, -5, 183, -30),
		m(null, -88, 162, 120),
		m(null, -144, 152, 91),
		m(null, -131, 143, 116),
		m(null, -106, 145, 134),
		m(null, -57, 136, 155),
		m(null, -124, 166, 136),
		m(null, -159, 160, 101),
		m(null, 163, 197, 181),
		m(null, 84, 152, 138),
		m(null, 74, 165, 130),
		m(null, 75, 148, 151),
		m(null, 38, 131, 167),
		m(null, 71, 141, 148),
		m(null, -4, 129, 145),
		m(null, 4, 129, 145),
		m(null, -29, 129, 159),
		m(null, 71, 165, 132),
		m(null, 67, 165, 135),
		m(null, 85, 188, 160),
		m(null, 86, 188, 56),
		m(null, 75, 188, 113),
		m(null, 96, 197, 157),
		m(null, 81, 188, 129),
		m(null, 78, 189, 153),
		m(null, -68, 222, -106),
		m(null, -43, 222, -109),
		m(null, -43, 222, -119),
		m(null, 143, 187, 99),
		m(null, 129, 188, 107),
		m(null, 115, 187, 17),
		m(null, 102, 195, -21),
		m(null, 122, 189, 97),
		m(null, 142, 187, 96),
		m(null, 143, 188, 16),
		m(null, 99, 197, 157),
		m(null, 108, 197, 203),
		m(null, 99, 197, 181),
		m(null, 159, 197, 181),
		m(null, 149, 197, 201),
		m(null, 129, 188, 115),
		m(null, 108, 197, 213),
		m(null, 150, 197, 213),
		m(null, -4, 148, -36),
		m(null, 4, 148, -36),
		m(null, 0, 148, -69),
		m(null, 0, 148, -50),
		m(null, 0, 169, -2),
		m(null, 10, 175, 0),
		m(null, -8, 175, -8),
		m(null, 12, 180, -12),
		m(null, -73, 201, -122),
		m(null, 128, 83, 59),
		m(null, 39, 84, 54),
		m(null, -19, 87, 62),
		m(null, 18, 85, 129),
		m(null, 54, 92, 54),
		m(null, 124, 83, 73),
		m(null, 51, 93, 119)
	};
}
