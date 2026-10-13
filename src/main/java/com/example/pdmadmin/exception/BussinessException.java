package com.example.pdmadmin.exception;

import com.example.pdmadmin.response.ResponseCode;
import lombok.Data;

@Data
public class BussinessException extends RuntimeException{
    /**
     * 自定义业务异常
     */
    private String message;
    /**
     * 异常码
     */
    private Integer code;
    public BussinessException(String message, Integer code) {
        this.message = message;
        this.code = code;
    }
    public BussinessException(String message) {
        this.message = message;
    }
    public BussinessException(ResponseCode responseCode
    ) {
        this.message = responseCode.getMessage();
        this.code = responseCode.getCode();
    }
}
