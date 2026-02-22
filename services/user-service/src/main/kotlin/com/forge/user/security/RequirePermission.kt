package com.forge.user.security

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * 权限校验注解
 * 使用方法: @RequirePermission(resource = "workspace", action = "write")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
annotation class RequirePermission(
    val resource: String,
    val action: String,
    val orgIdParam: String = ""
)