package com.example.pdmadmin.exception;

import com.example.pdmadmin.response.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerExceptionHandler {
    @ExceptionHandler(Exception.class)
    public R handleException(Exception e) {
        return R.fail(e.getMessage());
    }
    @ExceptionHandler(BussinessException.class)
    public R handleBussinessException(BussinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }


}
