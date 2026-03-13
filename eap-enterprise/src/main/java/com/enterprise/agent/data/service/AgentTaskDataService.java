package com.enterprise.agent.data.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.enterprise.agent.common.core.response.PageResult;
import com.enterprise.agent.data.entity.AgentTask;
import com.enterprise.agent.data.entity.AgentSubTask;
import com.enterprise.agent.data.mapper.AgentSubTaskMapper;
import com.enterprise.agent.data.mapper.AgentTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 任务数据服务 - 封装 CRUD 操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskDataService {

    private final AgentTaskMapper agentTaskMapper;
    private final AgentSubTaskMapper agentSubTaskMapper;

    @Transactional
    public AgentTask createTask(String taskName, String goal) {
        AgentTask task = new AgentTask();
        task.setTaskName(taskName);
        task.setGoal(goal);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        agentTaskMapper.insert(task);
        log.info("[DataService] 创建任务, id={}", task.getId());
        return task;
    }

    public AgentTask getById(Long id) {
        return agentTaskMapper.selectById(id);
    }

    public PageResult<AgentTask> getPage(long page, long size) {
        long offset = (page - 1) * size;
        List<AgentTask> records = agentTaskMapper.findPage(offset, size);
        long total = agentTaskMapper.countAll();
        return PageResult.of(records, total, page, size);
    }

    @Transactional
    public void updateStatus(Long taskId, String status) {
        UpdateWrapper<AgentTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId)
               .set("status", status)
               .set("updated_at", LocalDateTime.now());
        agentTaskMapper.update(null, wrapper);
    }

    @Transactional
    public void updateTaskResult(Long taskId, String plannerResult, String executorResult,
                                  Integer reviewerScore, String finalReport, String status) {
        UpdateWrapper<AgentTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId)
               .set(plannerResult != null, "planner_result", plannerResult)
               .set(executorResult != null, "executor_result", executorResult)
               .set(reviewerScore != null, "reviewer_score", reviewerScore)
               .set(finalReport != null, "final_report", finalReport)
               .set("status", status)
               .set("updated_at", LocalDateTime.now());
        agentTaskMapper.update(null, wrapper);
    }

    @Transactional
    public void saveSubTasks(Long taskId, List<AgentSubTask> subTasks) {
        // 先删除旧子任务
        agentSubTaskMapper.deleteByTaskId(taskId);
        // 批量插入新子任务
        subTasks.forEach(subTask -> {
            subTask.setTaskId(taskId);
            subTask.setCreatedAt(LocalDateTime.now());
            agentSubTaskMapper.insert(subTask);
        });
    }

    public List<AgentSubTask> getSubTasks(Long taskId) {
        return agentSubTaskMapper.findByTaskId(taskId);
    }
}
