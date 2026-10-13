package com.example.pdmadmin.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @RequestMapping("/sayhello")
    public String sayhello(@RequestParam String name){
        return "hello1" + name;
    }
}
