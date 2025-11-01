package com.nineforce.model;

public class ServiceResponse<T> {
    private String status;
    private T result;
    private String error;

    public ServiceResponse() {}

    public ServiceResponse(String status, T result, String error) {
        this.status = status;
        this.result = result;
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

