package org.yc.learn.rag.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常，携带 HTTP 状态码和稳定的错误码。
 *
 * @author zhengyanchuang
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
