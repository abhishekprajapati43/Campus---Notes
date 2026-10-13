package com.example.techcampushubbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform response envelope: { "success": ..., "message": ..., "data": ... }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, "Created", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
