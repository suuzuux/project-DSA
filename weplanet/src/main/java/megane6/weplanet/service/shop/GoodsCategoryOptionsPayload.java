package megane6.weplanet.service.shop;

import tools.jackson.databind.json.JsonMapper;
import megane6.weplanet.domain.entity.enumfolder.GoodsCategoryType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GoodsCategoryOptionsPayload(
		List<GoodsCategoryType> categories,
		Map<GoodsCategoryType, Map<String, List<String>>> optionsByCategory
) {
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	public static GoodsCategoryOptionsPayload empty() {
		return new GoodsCategoryOptionsPayload(List.of(), Map.of());
	}

	@SuppressWarnings("unchecked")
	public static GoodsCategoryOptionsPayload parse(String json) {
		if (json == null || json.isBlank()) {
			return empty();
		}
		try {
			Map<String, Object> root = MAPPER.readValue(json, Map.class);
			List<GoodsCategoryType> categories = new ArrayList<>();
			Object catRaw = root.get("categories");
			if (catRaw instanceof List<?> catList) {
				for (Object item : catList) {
					if (item == null) {
						continue;
					}
					categories.add(GoodsCategoryType.valueOf(String.valueOf(item).trim()));
				}
			}
			Map<GoodsCategoryType, Map<String, List<String>>> options = new LinkedHashMap<>();
			Object optRaw = root.get("options");
			if (optRaw instanceof Map<?, ?> optMap) {
				for (Map.Entry<?, ?> catEntry : optMap.entrySet()) {
					GoodsCategoryType type;
					try {
						type = GoodsCategoryType.valueOf(String.valueOf(catEntry.getKey()));
					} catch (IllegalArgumentException ex) {
						continue;
					}
					Map<String, List<String>> map = new LinkedHashMap<>();
					if (catEntry.getValue() instanceof Map<?, ?> catOpts) {
						for (Map.Entry<?, ?> opt : catOpts.entrySet()) {
							List<String> values = toValueList(opt.getValue());
							if (!values.isEmpty()) {
								map.put(String.valueOf(opt.getKey()), values);
							}
						}
					}
					if (!map.isEmpty()) {
						options.put(type, map);
					}
				}
			}
			return new GoodsCategoryOptionsPayload(categories, options);
		} catch (Exception ex) {
			throw new IllegalArgumentException("카테고리·옵션 형식이 올바르지 않습니다.");
		}
	}

	private static List<String> toValueList(Object raw) {
		List<String> values = new ArrayList<>();
		if (raw instanceof List<?> list) {
			for (Object v : list) {
				if (v != null && !String.valueOf(v).isBlank()) {
					values.add(String.valueOf(v).trim());
				}
			}
		} else if (raw != null && !String.valueOf(raw).isBlank()) {
			values.add(String.valueOf(raw).trim());
		}
		return values;
	}

	public String toJson() {
		try {
			Map<String, Object> root = new LinkedHashMap<>();
			root.put("categories", categories.stream().map(Enum::name).toList());
			Map<String, Object> options = new LinkedHashMap<>();
			for (Map.Entry<GoodsCategoryType, Map<String, List<String>>> e : optionsByCategory.entrySet()) {
				options.put(e.getKey().name(), e.getValue());
			}
			root.put("options", options);
			return MAPPER.writeValueAsString(root);
		} catch (Exception ex) {
		 return "{}";
		}
	}

	public static GoodsCategoryOptionsPayload fromEntity(
			List<GoodsCategoryType> categories,
			List<megane6.weplanet.domain.entity.GoodsOption> optionRows) {
		Map<GoodsCategoryType, Map<String, List<String>>> grouped = new LinkedHashMap<>();
		for (megane6.weplanet.domain.entity.GoodsOption row : optionRows) {
			String jsKey = toJsKey(row.getCategory(), row.getOptionKey());
			grouped
					.computeIfAbsent(row.getCategory(), k -> new LinkedHashMap<>())
					.computeIfAbsent(jsKey, k -> new ArrayList<>())
					.add(row.getOptionValue());
		}
		return new GoodsCategoryOptionsPayload(categories, grouped);
	}

	private static String toJsKey(GoodsCategoryType category, String dbKey) {
		return switch (category) {
			case CLOTHING -> "sizes";
			case SHOES -> "mm";
			case BAG -> switch (dbKey) {
				case "WIDTH" -> "width";
				case "HEIGHT" -> "height";
				case "DEPTH" -> "depth";
				default -> dbKey.toLowerCase();
			};
			case ACCESSORY, OTHER -> "note";
		};
	}

	public void validate() {
		if (categories == null || categories.isEmpty()) {
			return;
		}
		Set<GoodsCategoryType> selected = EnumSet.copyOf(categories);
		for (GoodsCategoryType type : selected) {
			Map<String, List<String>> opts = optionsByCategory.getOrDefault(type, Map.of());
			switch (type) {
				case CLOTHING -> {
					List<String> sizes = opts.getOrDefault("sizes", List.of());
					if (sizes.isEmpty()) {
						throw new IllegalArgumentException("의류 카테고리는 최소 1개 사이즈를 선택해주세요.");
					}
				}
				case SHOES -> {
					List<String> mm = opts.getOrDefault("mm", List.of());
					if (mm.isEmpty()) {
						throw new IllegalArgumentException("신발 카테고리는 최소 1개 사이즈(mm)를 선택해주세요.");
					}
				}
				case BAG -> {
					requireNumeric(opts, "width", "가방 가로");
					requireNumeric(opts, "height", "가방 높이");
					requireNumeric(opts, "depth", "가방 세로");
				}
				case ACCESSORY, OTHER -> { /* 옵션 없음 허용 */ }
			}
		}
	}

	private static void requireNumeric(Map<String, List<String>> opts, String key, String label) {
		List<String> values = opts.getOrDefault(key, List.of());
		if (values.isEmpty() || values.getFirst().isBlank()) {
			throw new IllegalArgumentException(label + " 치수를 입력해주세요.");
		}
		try {
			double v = Double.parseDouble(values.getFirst());
			if (v <= 0) {
				throw new IllegalArgumentException(label + " 치수는 0보다 커야 합니다.");
			}
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException(label + " 치수가 올바르지 않습니다.");
		}
	}
}
