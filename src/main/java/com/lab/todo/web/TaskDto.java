package com.lab.todo.web;

import java.time.Instant;

import com.lab.todo.model.Task;

/** Wire shape for a task. Also what gets cached, so it stays small and stable. */
public record TaskDto(Long id, String title, boolean completed, Instant createdAt) {

    public static TaskDto from(Task task) {
        return new TaskDto(task.getId(), task.getTitle(), task.isCompleted(), task.getCreatedAt());
    }
}
