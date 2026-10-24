package com.ywzt.datasync.api;

import lombok.Data;

@Data
public class ApiResult<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> ApiResult<T> success(T data) {
        return build(200, "操作成功", data);
    }

    public static <T> ApiResult<T> success(String msg) {
        return build(200, msg, null);
    }

    public static <T> ApiResult<T> fail(String msg) {
        return build(500, msg, null);
    }

    public static <T> ApiResult<T> build(int code, String msg, T data) {
        ApiResult<T> r = new ApiResult<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
