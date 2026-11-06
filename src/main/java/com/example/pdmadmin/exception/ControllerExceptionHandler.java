package com.example.pdmadmin.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import com.example.pdmadmin.response.R;
import com.example.pdmadmin.response.ResponseCode;
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

    /**
     * 角色不足（@SaCheckRole 校验失败）
     * 单独处理，否则会被上面的 Exception 兜住返回 500，
     * 前端无法区分「服务器出错」和「你没这个权限」。
     */
    @ExceptionHandler(NotRoleException.class)
    public R handleNotRoleException(NotRoleException e) {
        return R.fail(ResponseCode.NO_PERMISSION.getCode(),
                "无访问权限：该接口需要「" + e.getRole() + "」角色");
    }
}
