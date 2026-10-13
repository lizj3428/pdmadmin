package com.example.pdmadmin.response;

import lombok.Getter;

@Getter
public class R<T> {
    private Integer code;
    private String message;
    private T data;
    private  R(Integer code){
        this.code=code;
    }
    private  R(Integer code, String message){
        this.code=code;
        this.message=message;
    }
    private  R(Integer code, String message,T data){
        this.code=code;
        this.message=message;
        this.data=data;
    }

    public static <T> R<T> success(){
        return new R<>(ResponseCode.SUCCESS.getCode());
    }
    public static <T> R<T> success(String message){
        return new R<>(ResponseCode.SUCCESS.getCode(),message);
    }
    public static <T> R<T> data(T data){
        return new R<>(ResponseCode.SUCCESS.getCode(),ResponseCode.SUCCESS.getMessage(),data);
    }
    // 1.无参fail：使用默认的错误码500、默认提示"操作失败!"
    public static <T> R<T> fail(){
        return new R<>(ResponseCode.ERROR.getCode(),ResponseCode.ERROR.getMessage());
    }
    // 2.传字符串message：错误码固定500，自定义提示文字
    public static <T> R<T> fail(String message) {
        return new R<>(ResponseCode.ERROR.getCode(), message);
    }
    //3.传入ResponseCode枚举：用枚举里预设好的code和msg（可以扩展很多错误码，比如401未登录、403无权限）
    public static <T> R<T> fail(ResponseCode responseCode) {
        return new R<>(responseCode.getCode(), responseCode.getMessage());
    }
    //4.同时传code和message：自己随便写状态码和提示，不用枚举
    public static <T> R<T> fail(Integer code,String message) {
        return new R<>(code,message);
    }

}
