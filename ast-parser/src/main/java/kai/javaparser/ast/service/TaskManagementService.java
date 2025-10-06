package kai.javaparser.ast.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 任務管理服務
 * 用於管理非同步解析任務的狀態和結果
 */
@Service
public class TaskManagementService {

    private static final Logger logger = LoggerFactory.getLogger(TaskManagementService.class);

    /**
     * 任務狀態枚舉
     */
    public enum TaskStatus {
        PENDING, // 等待中
        PROCESSING, // 處理中
        COMPLETED, // 已完成
        FAILED // 失敗
    }

    /**
     * 任務信息類
     */
    public static class TaskInfo {
        private final String taskId;
        private final TaskStatus status;
        private final String result;
        private final String errorMessage;
        private final long createdAt;
        private final long updatedAt;

        public TaskInfo(String taskId, TaskStatus status, String result, String errorMessage, long createdAt,
                long updatedAt) {
            this.taskId = taskId;
            this.status = status;
            this.result = result;
            this.errorMessage = errorMessage;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        // Getters
        public String getTaskId() {
            return taskId;
        }

        public TaskStatus getStatus() {
            return status;
        }

        public String getResult() {
            return result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }

    private final ConcurrentHashMap<String, TaskInfo> tasks = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(0);

    /**
     * 創建新任務
     * 
     * @param future 非同步任務的CompletableFuture
     * @return 任務ID
     */
    public String createTask(CompletableFuture<String> future) {
        String taskId = "task_" + taskIdCounter.incrementAndGet();
        long now = System.currentTimeMillis();

        TaskInfo taskInfo = new TaskInfo(taskId, TaskStatus.PENDING, null, null, now, now);
        tasks.put(taskId, taskInfo);

        logger.info("創建新任務: {}", taskId);

        // 設置任務完成後的處理
        future.whenComplete((result, throwable) -> {
            long updateTime = System.currentTimeMillis();
            TaskStatus status;
            String finalResult = null;
            String errorMessage = null;

            if (throwable != null) {
                status = TaskStatus.FAILED;
                errorMessage = throwable.getMessage();
                logger.error("任務 {} 執行失敗", taskId, throwable);
            } else {
                status = TaskStatus.COMPLETED;
                finalResult = result;
                logger.info("任務 {} 執行完成", taskId);
            }

            TaskInfo completedTask = new TaskInfo(taskId, status, finalResult, errorMessage,
                    tasks.get(taskId).getCreatedAt(), updateTime);
            tasks.put(taskId, completedTask);
        });

        return taskId;
    }

    /**
     * 更新任務狀態為處理中
     * 
     * @param taskId 任務ID
     */
    public void markTaskAsProcessing(String taskId) {
        TaskInfo existingTask = tasks.get(taskId);
        if (existingTask != null && existingTask.getStatus() == TaskStatus.PENDING) {
            TaskInfo updatedTask = new TaskInfo(taskId, TaskStatus.PROCESSING, null, null,
                    existingTask.getCreatedAt(), System.currentTimeMillis());
            tasks.put(taskId, updatedTask);
            logger.info("任務 {} 開始處理", taskId);
        }
    }

    /**
     * 獲取任務信息
     * 
     * @param taskId 任務ID
     * @return 任務信息，如果不存在則返回null
     */
    public TaskInfo getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 檢查任務是否存在
     * 
     * @param taskId 任務ID
     * @return 是否存在
     */
    public boolean taskExists(String taskId) {
        return tasks.containsKey(taskId);
    }

    /**
     * 清理過期任務（可選功能，用於內存管理）
     * 
     * @param maxAgeMillis 最大存活時間（毫秒）
     */
    public void cleanupExpiredTasks(long maxAgeMillis) {
        long now = System.currentTimeMillis();
        tasks.entrySet().removeIf(entry -> {
            TaskInfo task = entry.getValue();
            boolean isExpired = (now - task.getUpdatedAt()) > maxAgeMillis;
            if (isExpired) {
                logger.info("清理過期任務: {}", entry.getKey());
            }
            return isExpired;
        });
    }
}
