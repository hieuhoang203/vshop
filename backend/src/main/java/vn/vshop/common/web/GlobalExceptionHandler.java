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
                    case SHOP_NOT_FOUND, PRODUCT_NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case ACCOUNT_SUSPENDED -> HttpStatus.FORBIDDEN;
                    case INVALID_CATALOG_FILTER -> HttpStatus.BAD_REQUEST;
                    case SHOP_ALREADY_EXISTS, INVALID_SHOP_STATE -> HttpStatus.CONFLICT;
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
        HttpMessageNotReadableException.class
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
