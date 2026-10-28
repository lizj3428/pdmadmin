package com.example.pdmadmin.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.example.pdmadmin.response.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerExceptionHandler {
    @ExceptionHandler(Exception.class)
    public R handleException(Exception e) {
        return R.fail(e.getMessage());
    }
    @ExceptionHandler(BusinessException.class)
    public R handleBusinessException(BusinessException e) {

        return R.fail(e.getCode(), e.getMessage());
    }
    @ExceptionHandler(NotLoginException.class)
    public R handleNotLoginException(NotLoginException e) {
        return R.fail(e.getCode(), e.getMessage());
    }


}
