package vn.vshop.modules.order.model;

import java.util.List;
import vn.vshop.modules.order.entity.ReturnCase;
import vn.vshop.modules.order.entity.ReturnCaseEvent;

public record ReturnCaseDetail(ReturnCase returnCase, List<ReturnCaseEvent> events) {
    public ReturnCaseDetail {
        events = List.copyOf(events);
    }
}
