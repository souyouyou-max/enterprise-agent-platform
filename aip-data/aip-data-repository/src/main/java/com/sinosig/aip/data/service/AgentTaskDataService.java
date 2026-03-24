package com.sinosig.aip.data.service;

import com.sinosig.aip.common.core.response.PageResult;
import com.sinosig.aip.data.entity.AgentSubTask;
import com.sinosig.aip.data.entity.AgentTask;

import java.util.List;

/**
 * Agent 任务数据服务接口
 * <p>
 * 实现类：{@link com.sinosig.aip.data.service.impl.AgentTaskDataServiceImpl}
 */
public interface AgentTaskDataService {

    AgentTask createTask(String taskName, String goal);

    AgentTask getById(Long id);

    PageResult<AgentTask> getPage(long page, long size);

    void updateStatus(Long taskId, String status);
    boolean updateStatusIfCurrent(Long taskId, String expectedCurrentStatus, String targetStatus);

    void updateTaskResult(Long taskId, String plannerResult, String executorResult,
                          Integer reviewerScore, String finalReport, String status);
    boolean updateTaskResultIfCurrent(Long taskId, String expectedCurrentStatus,
                                      String plannerResult, String executorResult,
                                      Integer reviewerScore, String finalReport, String targetStatus);

    void saveSubTasks(Long taskId, List<AgentSubTask> subTasks);

    List<AgentSubTask> getSubTasks(Long taskId);
}
