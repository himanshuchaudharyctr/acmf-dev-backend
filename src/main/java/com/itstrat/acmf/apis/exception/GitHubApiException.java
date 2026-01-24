package com.itstrat.acmf.apis.exception;

public class GitHubApiException extends RuntimeException {
        private final int statusCode;

        public GitHubApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
}
