package com.xuan.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xuan.dto.OperationLogQueryDTO;
import com.xuan.entity.SysOperationLog;
import com.xuan.result.PageResult;
import com.xuan.vo.OperationLogDetailVO;
import com.xuan.vo.OperationLogVO;

import java.util.List;

/**
 * 操作日志业务接口，后台只能查询和清理，不能创建或编辑。
 */
public interface IOperationLogService extends IService<SysOperationLog> {

    /** 分页查询后台操作日志，需要 ALL 范围，按指定字段筛选。 */
    PageResult<OperationLogVO> listLogs(OperationLogQueryDTO query);

    /** 根据id查询操作日志详情，需要 ALL 范围，返回操作基本信息及已记录的完整业务参数。 */
    OperationLogDetailVO detail(long id);

    /** 根据id删除操作日志，删除和本次清理的成功日志一起提交；记录不存在时返回 404。 */
    void delete(long id);
    /** 批量删除操作日志，单条也走此入口，所有数据库修改一起提交。 */
    void deleteBatch(List<String> ids);
}
