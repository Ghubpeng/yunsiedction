package com.yunsie.module.sys.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 客服联系方式配置表（sys_customer_service，单行 id=1，CONFLICTS #27）。
 * 配置化联系客服（电话/微信/二维码/服务时间/开关）；不接第三方客服系统。
 */
@Getter
@Setter
@TableName("sys_customer_service")
public class SysCustomerService extends BaseEntity {

    /** 客服电话（空=未配置） */
    private String phone;

    /** 客服微信号（空=未配置） */
    private String wechat;

    /** 客服微信二维码图片 URL（空=未配置） */
    private String qrCodeUrl;

    /** 服务时间文案 */
    private String serviceTime;

    /** 是否启用客服入口: 0-关闭 1-开启 */
    private Integer enabled;
}
