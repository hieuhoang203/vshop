package vn.vshop.common.web.dto;

public record ApiErrorResponse(String code, String message, String correlationId) {}
