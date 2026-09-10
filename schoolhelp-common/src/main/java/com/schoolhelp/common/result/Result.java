package com.schoolhelp.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包装
 */
@Data
public class Result<T> implements Serializable {

    private Integer code;      // 200成功，其他失败
    private String message;
    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    /** 失败（同 error，语义别名） */
    public static <T> Result<T> fail(int code, String message) {
        return error(code, message);
    }

    /** 是否成功 */
    public boolean isSuccess() {
        return code != null && code == 200;
    }
}
