package com.example.pdmadmin.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.pdmadmin.entity.Admin;
import com.example.pdmadmin.mapper.AdminMapper;
import com.example.pdmadmin.service.AdminService;
import org.springframework.stereotype.Service;

@Service
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements AdminService {
}
