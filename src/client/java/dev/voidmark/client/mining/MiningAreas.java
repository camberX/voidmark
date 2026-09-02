package dev.voidmark.client.mining;

import net.minecraft.core.BlockPos;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Named Dwarven Mines regions used by {@code [Area] Titanium} commissions.
 * Centers are Hypixel emissary / waypoint coords; a block belongs to the
 * nearest region that still covers it so adjacent quarries do not leak ESP.
 */
public final class MiningAreas {
	private MiningAreas() {
	}

	public enum Region {
		LAVA_SPRINGS("Lava Springs", 58, 198, -8, 85, "lava springs"),
		CLIFFSIDE_VEINS("Cliffside Veins", 42, 134, 22, 80, "cliffside"),
		RAMPARTS_QUARRY("Rampart's Quarry", -89, 150, -4, 80, "rampart"),
		UPPER_MINES("Upper Mines", -128, 172, -60, 80, "upper mines"),
		ROYAL_MINES("Royal Mines", 171, 150, 40, 95, "royal mines");

		private final String label;
		private final int x;
		private final int y;
		private final int z;
		private final int radiusSq;
		private final String token;

		Region(String label, int x, int y, int z, int radius, String token) {
			this.label = label;
			this.x = x;
			this.y = y;
			this.z = z;
			this.radiusSq = radius * radius;
			this.token = token;
		}

		public String label() {
			return label;
		}

		boolean covers(int bx, int by, int bz) {
			return distSq(bx, by, bz) <= radiusSq;
		}

		long distSq(int bx, int by, int bz) {
			long dx = bx - x;
			long dy = by - y;
			long dz = bz - z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	public static Region of(int x, int y, int z) {
		Region best = null;
		long bestDist = Long.MAX_VALUE;
		for (Region region : Region.values()) {
			long dist = region.distSq(x, y, z);
			if (dist <= region.radiusSq && dist < bestDist) {
				bestDist = dist;
				best = region;
			}
		}
		return best;
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
			Region region = match(key);
			if (region == null) {
				unrestricted = true;
			} else {
				areas.add(region);
			}
		}
		if (!any) {
			return TitaniumFilter.NONE;
		}
		if (unrestricted) {
			return TitaniumFilter.ALL;
		}
		return TitaniumFilter.areas(areas);
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
			if (regions == null || regions.isEmpty()) {
				return ALL;
			}
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
}
