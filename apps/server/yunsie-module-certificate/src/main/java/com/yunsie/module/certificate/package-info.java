/**
 * certificate 域：证书无限级分类树。
 *
 * <p>硬性要求：通用树结构（邻接表 parent_id + 物化路径 path + 排序 sort + 启用/禁用），
 * 支持无限层级，后台可增删改移排序；系统不得写死“护士”等任何具体证书（平台化）。</p>
 *
 * <p>模块边界规则同 sys 域。</p>
 */
package com.yunsie.module.certificate;
