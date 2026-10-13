package com.example.valkey;

/**
 * valkey 访问异常的统一种类。
 *
 * <p>操作执行、命令执行以及连接创建过程中出现的异常都会转换成本异常，原始异常通过 {@link
 * #getCause()} 保留，便于排查根因。
 */
public class ValkeyAccessException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ValkeyAccessException(String message) {
    super(message);
  }

  public ValkeyAccessException(String message, Throwable cause) {
    super(message, cause);
  }
}
