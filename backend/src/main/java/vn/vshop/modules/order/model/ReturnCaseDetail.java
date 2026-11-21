package vn.vshop.modules.order.model;

import java.util.List;
import vn.vshop.modules.order.entity.ReturnCase;
import vn.vshop.modules.order.entity.ReturnCaseEvent;

public record ReturnCaseDetail(
        ReturnCase returnCase,
        List<ReturnCaseEvent> events,
        List<vn.vshop.modules.order.entity.ReturnCaseItem> items) {
    public ReturnCaseDetail {
        events = List.copyOf(events);
        items = List.copyOf(items);
    }
}
