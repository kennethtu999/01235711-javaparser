package com.example.case2;

public class ActionException extends Exception {
    private String statusCode;

    public ActionException() {
        super();
    }

    public ActionException(String message) {
        super(message);
    }

    public ActionException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public Status getStatus() {
        return new Status();
    }

    public static class Status {
        private String statusCode;

        public String getStatusCode() {
            return statusCode;
        }
    }
}
