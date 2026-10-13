package megane6.weplanet.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 관리자 목록의 페이지 분할과 필터를 유지하는 공통 이동 링크. */
public final class AdminPagination {
    public static final int DEFAULT_PAGE_LINKS = 5;
    public static final int KEYWORD_PAGE_LINKS = 10;
    private AdminPagination() {}

    public static int requestedPage(String parameter) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return 0;
        try {
            String value = servlet.getRequest().getParameter(parameter);
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static <T> void paginate(Model model, String name, List<T> items,
                                   int requestedPage, int size, String parameter) {
        paginate(model, name, items, requestedPage, size, parameter, null);
    }

    public static <T> void paginate(Model model, String name, List<T> items,
                                   int requestedPage, int size, String parameter, String path) {
        paginate(model, name, items, requestedPage, size, parameter, path, DEFAULT_PAGE_LINKS);
    }

    public static <T> void paginate(Model model, String name, List<T> items,
                                   int requestedPage, int size, String parameter, String path, int pageLinks) {
        int totalPages = Math.max(1, (items.size() + size - 1) / size);
        int page = Math.min(Math.max(0, requestedPage), totalPages - 1);
        int from = page * size;
        model.addAttribute(name, items.subList(from, Math.min(from + size, items.size())));
        model.addAttribute(name + "Total", items.size());
        addNavigation(model, name + "Pagination", page, totalPages, parameter, path, pageLinks);
    }

    public static void addNavigation(Model model, String name, int page, int pages, String parameter) {
        addNavigation(model, name, page, pages, parameter, null, DEFAULT_PAGE_LINKS);
    }

    private static void addNavigation(Model model, String name, int page, int pages, String parameter,
                                      String path, int pageLinks) {
        int totalPages = Math.max(1, pages);
        int current = Math.min(Math.max(0, page), totalPages - 1);
        int groupStart = current / pageLinks * pageLinks;
        int groupEnd = Math.min(groupStart + pageLinks, totalPages);
        List<PageLink> links = new ArrayList<>();
        for (int index = groupStart; index < groupEnd; index++) {
            links.add(new PageLink(index + 1, pageUrl(parameter, index, path), index == current));
        }
        model.addAttribute(name, new Navigation(current, totalPages, links,
                groupStart > 0 ? pageUrl(parameter, groupStart - 1, path) : null,
                groupEnd < totalPages ? pageUrl(parameter, groupEnd, path) : null));
    }

    private static String pageUrl(String parameter, int page, String path) {
        var attributes = RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes instanceof ServletRequestAttributes servlet
                ? servlet.getRequest() : null;
        var builder = UriComponentsBuilder.fromPath(path != null ? path : request != null ? request.getRequestURI() : "/admin");
        Map<String, Object> variables = new LinkedHashMap<>();
        if (request != null && "GET".equals(request.getMethod())) {
            request.getParameterMap().forEach((key, values) -> {
                if (key.equals(parameter)) return;
                for (String value : values) {
                    String variable = "value" + variables.size();
                    variables.put(variable, value);
                    builder.queryParam(key, "{" + variable + "}");
                }
            });
        }
        return builder.queryParam(parameter, page).encode().buildAndExpand(variables).toUriString();
    }

    public record PageLink(int number, String url, boolean active) {}
    public record Navigation(int number, int totalPages, List<PageLink> links,
                             String previousUrl, String nextUrl) {}
}
