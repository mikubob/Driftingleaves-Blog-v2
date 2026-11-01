package com.xuan.vo;

/** 一个文件的实际执行结果，失败不掩盖其他文件已经成功。 */
public record BatchFileResultVO(Long id, int httpStatus, String code, String msg) {
}
