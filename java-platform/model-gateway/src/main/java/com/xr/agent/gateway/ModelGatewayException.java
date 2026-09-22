package com.xr.agent.gateway;

import java.util.Objects;

public final class ModelGatewayException extends RuntimeException {

    private final String errorCode;

    public ModelGatewayException(String errorCode) {
        this(errorCode, null);
    }

    public ModelGatewayException(String errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode"), cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
