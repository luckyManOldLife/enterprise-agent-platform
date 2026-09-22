package com.xr.agent.server;

import java.util.Map;

final class ApiExceptionHandler {

    private ApiExceptionHandler() {
    }

    static ApiError handleBadRequest(IllegalArgumentException exception) {
        return new ApiError(400, Map.of(
                "code", "BAD_REQUEST",
                "message", safeMessage(exception)));
    }

    static ApiError handleConflict(IllegalStateException exception) {
        return new ApiError(409, Map.of(
                "code", "CONFLICT",
                "message", safeMessage(exception)));
    }

    static ApiError handleUnexpected(Exception exception) {
        return new ApiError(500, Map.of(
                "code", "INTERNAL_ERROR",
                "message", "Internal server error"));
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Request cannot be processed" : message;
    }

    record ApiError(int status, Map<String, Object> body) {
    }
}
