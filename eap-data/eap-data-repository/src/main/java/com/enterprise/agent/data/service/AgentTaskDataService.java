package com.enterprise.agent.data.service;

import com.enterprise.agent.common.core.response.PageResult;
import com.enterprise.agent.data.entity.AgentSubTask;
import com.enterprise.agent.data.entity.AgentTask;

import java.util.List;

/**
 * Agent 任务数据服务接口
 * <p>
 * 实现类：{@link com.enterprise.agent.data.service.impl.AgentTaskDataServiceImpl}
 */
public interface AgentTaskDataService {

    AgentTask createTask(String taskName, String goal);

    AgentTask getById(Long id);

    PageResult<AgentTask> getPage(long page, long size);

    void updateStatus(Long taskId, String status);

    void updateTaskResult(Long taskId, String plannerResult, String executorResult,
                          Integer reviewerScore, String finalReport, String status);

    void saveSubTasks(Long taskId, List<AgentSubTask> subTasks);

    List<AgentSubTask> getSubTasks(Long taskId);
}
