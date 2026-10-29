package com.example.pdmadmin.controller;

import com.example.pdmadmin.websocket.MessagePushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 临时验证接口（阶段0地基自测用）
 * 【作用】用来手动触发一次"实时推送"，方便你在浏览器里看到弹窗，
 *        确认 WebSocket 链路（握手 → 登记 → 推送 → 前端弹窗）是通的。
 *        真实业务里推送由"审核通过 / 定时推送"等动作触发，不需要这个接口；
 *        等阶段2推送管理做完，这个文件可以删掉。
 */
@RestController
@RequestMapping("/test")
public class TestPushController {

    @Autowired
    private MessagePushService messagePushService;

    @GetMapping("/push")
    public String push(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "测试推送") String title,
            @RequestParam(defaultValue = "这是一条来自后端的实时消息") String content) {
        String json = "{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}";
        if (userId != null) {
            messagePushService.sendToUser(userId, json);
        } else {
            messagePushService.sendToAll(json);
        }
        return "push sent";
    }
}
