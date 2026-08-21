/**
 * learning-profile 域对外契约包：仅暴露 LearnQueryApi（未来 AI 上下文注入的最小视图）。
 * 其他域只允许经本包访问本域，禁止直连 learn_* 表（模块边界铁律）。
 */
package com.yunsie.module.learning.profile.api;
