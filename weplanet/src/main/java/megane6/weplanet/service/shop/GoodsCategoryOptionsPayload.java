package megane6.weplanet.service.shop;

import tools.jackson.databind.json.JsonMapper;
import megane6.weplanet.domain.entity.GoodsOption;
import megane6.weplanet.domain.entity.GoodsVariant;
import megane6.weplanet.domain.entity.enumfolder.GoodsCategoryType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 관리자 굿즈 폼 hidden JSON (categories, stocks, attributes). */
public record GoodsCategoryOptionsPayload(
		List<GoodsCategoryType> categories,
		Map<String, Object> stocks,
		Map<String, Map<String, String>> attributes
) {
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	public static GoodsCategoryOptionsPayload empty() {
		return new GoodsCategoryOptionsPayload(List.of(), Map.of(), Map.of());
	}

	@SuppressWarnings("unchecked")
	public static GoodsCategoryOptionsPayload parse(String json) {
		if (json == null || json.isBlank()) {
			return empty();
		}
		try {
			Map<String, Object> root = MAPPER.readValue(json, Map.class);
			List<GoodsCategoryType> categories = new ArrayList<>();
			if (root.get("categories") instanceof List<?> list) {
				for (Object item : list) {
					if (item != null) {
						categories.add(GoodsCategoryType.valueOf(String.valueOf(item).trim()));
					}
				}
			}
			Map<String, Object> stocks = new LinkedHashMap<>();
			if (root.get("stocks") instanceof Map<?, ?> map) {
				map.forEach((k, v) -> stocks.put(String.valueOf(k), v));
			}
			Map<String, Map<String, String>> attributes = new LinkedHashMap<>();
			if (root.get("attributes") instanceof Map<?, ?> map) {
				map.forEach((k, v) -> {
					Map<String, String> inner = new LinkedHashMap<>();
					if (v instanceof Map<?, ?> im) {
						im.forEach((ik, iv) -> {
							if (iv != null && !String.valueOf(iv).isBlank()) {
								inner.put(String.valueOf(ik), String.valueOf(iv).trim());
							}
						});
					}
					if (!inner.isEmpty()) {
						attributes.put(String.valueOf(k), inner);
					}
				});
			}
			return new GoodsCategoryOptionsPayload(categories, stocks, attributes);
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalArgumentException("error.goods.optionsInvalid");
		}
	}

	public String toJson() {
		try {
			Map<String, Object> root = new LinkedHashMap<>();
			root.put("categories", categories.stream().map(Enum::name).toList());
			root.put("stocks", stocks);
			root.put("attributes", attributes);
			return MAPPER.writeValueAsString(root);
		} catch (Exception ex) {
			return "{}";
		}
	}

	public static GoodsCategoryOptionsPayload fromEntity(
			List<GoodsCategoryType> categories,
			List<GoodsVariant> variants,
			List<GoodsOption> optionRows) {
		Map<String, Object> stocks = new LinkedHashMap<>();
		Map<String, Integer> clothing = new LinkedHashMap<>();
		Map<String, Integer> shoes = new LinkedHashMap<>();
		Integer defaultStock = null;
		for (GoodsVariant v : variants) {
			if (GoodsVariant.KEY_SIZE.equals(v.getOptionKey())) {
				clothing.put(v.getOptionValue(), v.getStockQuantity());
			} else if (GoodsVariant.KEY_SHOE_MM.equals(v.getOptionKey())) {
				shoes.put(v.getOptionValue(), v.getStockQuantity());
			} else if (GoodsVariant.KEY_DEFAULT.equals(v.getOptionKey())) {
				defaultStock = v.getStockQuantity();
			}
		}
		if (!clothing.isEmpty()) {
			stocks.put("CLOTHING", clothing);
		}
		if (!shoes.isEmpty()) {
			stocks.put("SHOES", shoes);
		}
		if (defaultStock != null) {
			stocks.put("DEFAULT", defaultStock);
		}

		Map<String, Map<String, String>> attributes = new LinkedHashMap<>();
		for (GoodsOption row : optionRows) {
			String jsKey = switch (row.getOptionKey()) {
				case "WIDTH" -> "width";
				case "HEIGHT" -> "height";
				case "DEPTH" -> "depth";
				default -> "note";
			};
			attributes
					.computeIfAbsent(row.getCategory().name(), k -> new LinkedHashMap<>())
					.put(jsKey, row.getOptionValue());
		}
		return new GoodsCategoryOptionsPayload(categories, stocks, attributes);
	}

	public void validate() {
		Set<GoodsCategoryType> selected = categories.isEmpty()
				? EnumSet.noneOf(GoodsCategoryType.class)
				: EnumSet.copyOf(categories);

		if (selected.contains(GoodsCategoryType.CLOTHING)) {
			Map<String, Integer> sizes = sizeMap("CLOTHING");
			if (sizes.isEmpty()) {
				throw new IllegalArgumentException("error.goods.clothingSizesRequired");
			}
			sizes.values().forEach(GoodsCategoryOptionsPayload::requireNonNegative);
		}
		if (selected.contains(GoodsCategoryType.SHOES)) {
			Map<String, Integer> mm = sizeMap("SHOES");
			if (mm.isEmpty()) {
				throw new IllegalArgumentException("error.goods.shoeSizesRequired");
			}
			mm.values().forEach(GoodsCategoryOptionsPayload::requireNonNegative);
		}

		boolean needsDefault = selected.isEmpty()
				|| selected.stream().anyMatch(c -> !c.isHasSelectableOptions());
		if (needsDefault) {
			requireNonNegative(defaultStock());
		}

		if (selected.contains(GoodsCategoryType.BAG)) {
			Map<String, String> bag = attributes.getOrDefault("BAG", Map.of());
			requirePositiveNumber(bag.get("width"), "width");
			requirePositiveNumber(bag.get("height"), "height");
			requirePositiveNumber(bag.get("depth"), "depth");
		}
	}

	@SuppressWarnings("unchecked")
	public Map<String, Integer> sizeMap(String categoryKey) {
		Object raw = stocks.get(categoryKey);
		Map<String, Integer> out = new LinkedHashMap<>();
		if (raw instanceof Map<?, ?> map) {
			map.forEach((k, v) -> {
				if (k == null || v == null || String.valueOf(k).isBlank()) {
					return;
				}
				out.put(String.valueOf(k).trim(), parseStock(v));
			});
		}
		return out;
	}

	public int defaultStock() {
		Object raw = stocks.get("DEFAULT");
		if (raw == null) {
			return 0;
		}
		return parseStock(raw);
	}

	public Set<GoodsCategoryType> selectedSet() {
		return categories.isEmpty()
				? Set.of()
				: new java.util.LinkedHashSet<>(categories);
	}

	private static int parseStock(Object v) {
		try {
			int n = Integer.parseInt(String.valueOf(v).trim());
			if (n < 0) {
				throw new IllegalArgumentException("error.goods.stockNegative");
			}
			return n;
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("error.goods.stockInvalid");
		}
	}

	private static void requireNonNegative(int n) {
		if (n < 0) {
			throw new IllegalArgumentException("error.goods.stockNegative");
		}
	}

	// 치수별 메시지 키로 던진다.
	private static void requirePositiveNumber(String raw, String dim) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("error.goods.bag." + dim + ".required");
		}
		try {
			double v = Double.parseDouble(raw.trim());
			if (v <= 0) {
				throw new IllegalArgumentException("error.goods.bag." + dim + ".positive");
			}
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("error.goods.bag." + dim + ".invalid");
		}
	}
}
