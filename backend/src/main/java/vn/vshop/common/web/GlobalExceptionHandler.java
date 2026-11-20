package vn.vshop.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.vshop.common.exception.BusinessException;
import vn.vshop.common.web.dto.ApiErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> business(
            BusinessException exception, HttpServletRequest request) {
        HttpStatus status =
                switch (exception.errorCode()) {
                    case SHOP_NOT_FOUND,
                            PRODUCT_NOT_FOUND,
                            RESERVATION_NOT_FOUND,
                            QUOTE_NOT_FOUND,
                            ADDRESS_NOT_FOUND,
                            POLICY_NOT_FOUND,
                            ORDER_NOT_FOUND ->
                            HttpStatus.NOT_FOUND;
                    case ACCOUNT_SUSPENDED -> HttpStatus.FORBIDDEN;
                    case CARRIER_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
                    case INVALID_CATALOG_FILTER,
                            INVALID_PRODUCT_INPUT,
                            INVALID_STOCK_INPUT,
                            INVALID_CART_INPUT,
                            INVALID_ADDRESS_INPUT,
                            INVALID_POLICY_INPUT,
                            INVALID_ORDER_INPUT,
                            INVALID_PICKUP_INPUT ->
                            HttpStatus.BAD_REQUEST;
                    case SHOP_ALREADY_EXISTS,
                            INVALID_SHOP_STATE,
                            INVALID_PRODUCT_STATE,
                            DUPLICATE_SKU,
                            VERSION_CONFLICT,
                            INSUFFICIENT_STOCK,
                            IDEMPOTENCY_CONFLICT,
                            INVALID_RESERVATION_STATE,
                            SKU_UNAVAILABLE,
                            CART_EMPTY,
                            ADDRESS_LIMIT_REACHED,
                            INVALID_POLICY_STATE,
                            INVALID_ORDER_STATE,
                            CARRIER_NOT_CONFIGURED,
                            CARRIER_REJECTED,
                            PICKUP_ADDRESS_REQUIRED,
                            CHECKOUT_POLICY_MISSING,
                            QUOTE_STALE,
                            QUOTE_ADDRESS_REQUIRED,
                            COD_LIMIT_EXCEEDED,
                            SELF_PURCHASE_NOT_ALLOWED ->
                            HttpStatus.CONFLICT;
                };
        return ResponseEntity.status(status)
                .body(
                        new ApiErrorResponse(
                                exception.code(), exception.getMessage(), correlationId(request)));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        org.springframework.web.bind.MissingRequestHeaderException.class
    })
    public ResponseEntity<ApiErrorResponse> invalid(
            Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        new ApiErrorResponse(
                                "INVALID_INPUT",
                                "Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.",
                                correlationId(request)));
    }

    private String correlationId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute("correlationId"));
    }
}
