package com.animegraph.common;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.neo4j.driver.exceptions.Neo4jException.class)
    ResponseEntity<?> graphUnavailable(Exception exception) {
        LOG.warn("Graph query failed", exception);
        return ResponseEntity.status(503).body(ApiResponse.error("GRAPH_UNAVAILABLE", "图谱服务暂时不可用", null));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<?> databaseUnavailable(Exception exception) {
        LOG.error("Database operation failed", exception);
        return ResponseEntity.status(503).body(ApiResponse.error("DATABASE_UNAVAILABLE", "系统数据服务暂时不可用", null));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge(Exception exception) {
        return ResponseEntity.status(413).body(ApiResponse.error("FILE_TOO_LARGE", "文件不得超过20MB", null));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> businessError(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.error(exception.code(), exception.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalidBody(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(
                error -> error.getField(), error -> String.valueOf(error.getDefaultMessage()), (first, next) -> first));
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "请求参数不正确", errors));
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<?> invalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", "请求参数不正确", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> notFound(NoResourceFoundException exception) {
        return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", "请求的资源不存在", null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception exception) {
        LOG.error("Unhandled request failure", exception);
        return ResponseEntity.internalServerError().body(ApiResponse.error("INTERNAL_ERROR", "服务暂时不可用", null));
    }
}
