package com.testproject.calendar.api;

import com.testproject.calendar.generated.model.ApiError;

public class ApiFailure extends RuntimeException {

    private final int status;
    private final ApiError error;

    public ApiFailure(int status, ApiError error) {
        super(error.getMessage());
        this.status = status;
        this.error = error;
    }

    public int status() {
        return status;
    }

    public ApiError error() {
        return error;
    }
}
