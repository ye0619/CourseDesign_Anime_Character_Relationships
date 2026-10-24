package com.animegraph.common;

import org.slf4j.MDC;

public record ApiResponse<T>(String code, String message, T data, String requestId) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "", data, MDC.get("requestId"));
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, MDC.get("requestId"));
    }
}
