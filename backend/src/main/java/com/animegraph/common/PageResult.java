package com.animegraph.common;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int pageSize, long total) {
    public static int offset(int page, int pageSize) {
        if (page < 1 || page > 100000 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "分页参数超出范围");
        }
        return (page - 1) * pageSize;
    }
}
