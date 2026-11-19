package vn.vshop.common.model;

import java.util.List;

public record SearchResult<T>(List<T> items, long total, int page, int size) {
    public SearchResult {
        items = List.copyOf(items);
    }
}
