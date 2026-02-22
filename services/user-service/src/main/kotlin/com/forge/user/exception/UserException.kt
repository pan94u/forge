package com.forge.user.exception

/**
 * 用户相关异常基类
 */
open class UserException(message: String) : RuntimeException(message)

class UsernameAlreadyExistsException(message: String) : UserException(message)
class EmailAlreadyExistsException(message: String) : UserException(message)
class PhoneAlreadyExistsException(message: String) : UserException(message)
class UserNotFoundException(message: String) : UserException(message)
class InvalidPasswordException(message: String) : UserException(message)

/**
 * 认证异常
 */
class AuthenticationException(message: String) : RuntimeException(message)

/**
 * 权限异常
 */
class PermissionDeniedException(message: String) : RuntimeException(message)

/**
 * 资源不存在异常
 */
class ResourceNotFoundException(message: String) : RuntimeException(message)