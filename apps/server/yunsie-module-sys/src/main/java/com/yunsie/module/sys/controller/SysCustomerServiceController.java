package com.yunsie.module.sys.controller;

import com.yunsie.common.api.Result;
import com.yunsie.module.sys.dto.CustomerServiceUpsertReq;
import com.yunsie.module.sys.entity.SysCustomerService;
import com.yunsie.module.sys.mapper.SysCustomerServiceMapper;
import com.yunsie.module.sys.vo.CustomerServiceVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服配置接口（Stage 2.2 商业入口基础）：
 * - 公开读取：GET /api/v1/service/customer-service（SecurityConfig 放行，登录页帮助入口可用）
 * - 管理端：GET/PUT /api/v1/sys/customer-service（权限点 sys:service:config）
 * 单行配置（id=1，V11 种子）；不接第三方客服系统。
 */
@RestController
@RequiredArgsConstructor
public class SysCustomerServiceController {

    private final SysCustomerServiceMapper mapper;

    /** 公开读取：返回配置视图（enabled=0 时前端展示「暂未开通」） */
    @GetMapping("/api/v1/service/customer-service")
    public Result<CustomerServiceVO> publicGet() {
        return Result.ok(toVO(load()));
    }

    /** 管理端读取（含全部字段） */
    @GetMapping("/api/v1/sys/customer-service")
    @PreAuthorize("@perm.has('sys:service:config')")
    public Result<CustomerServiceVO> adminGet() {
        return Result.ok(toVO(load()));
    }

    /** 管理端更新（单行 upsert，id=1） */
    @PutMapping("/api/v1/sys/customer-service")
    @PreAuthorize("@perm.has('sys:service:config')")
    public Result<CustomerServiceVO> update(@Valid @RequestBody CustomerServiceUpsertReq req) {
        SysCustomerService cfg = load();
        if (cfg == null) {
            cfg = new SysCustomerService();
            cfg.setId(1L);
            cfg.setPhone(trimToNull(req.phone()));
            cfg.setWechat(trimToNull(req.wechat()));
            cfg.setQrCodeUrl(trimToNull(req.qrCodeUrl()));
            cfg.setServiceTime(trimToNull(req.serviceTime()));
            cfg.setEnabled(req.enabled());
            mapper.insert(cfg);
        } else {
            cfg.setPhone(trimToNull(req.phone()));
            cfg.setWechat(trimToNull(req.wechat()));
            cfg.setQrCodeUrl(trimToNull(req.qrCodeUrl()));
            cfg.setServiceTime(trimToNull(req.serviceTime()));
            cfg.setEnabled(req.enabled());
            mapper.updateById(cfg);
        }
        return Result.ok(toVO(load()));
    }

    private SysCustomerService load() {
        return mapper.selectById(1L);
    }

    private String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private CustomerServiceVO toVO(SysCustomerService cfg) {
        if (cfg == null) {
            return new CustomerServiceVO(null, null, null, null, 0);
        }
        return new CustomerServiceVO(cfg.getPhone(), cfg.getWechat(), cfg.getQrCodeUrl(),
                cfg.getServiceTime(), cfg.getEnabled() == null ? 0 : cfg.getEnabled());
    }
}
