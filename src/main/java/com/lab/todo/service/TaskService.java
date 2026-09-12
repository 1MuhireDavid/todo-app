package com.lab.todo.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.todo.model.Task;
import com.lab.todo.repository.TaskRepository;
import com.lab.todo.web.ReadResult;
import com.lab.todo.web.TaskDto;

/**
 * Read-through cache over PostgreSQL.
 *
 * <p>Reads look in Redis first and fall back to the database, writing what they
 * found back into the cache under a short TTL. Writes go straight to PostgreSQL
 * through the RDS Proxy and then delete the keys they affected, so the next
 * read repopulates from the source of truth.
 *
 * <p>Invalidate rather than update: a write that rewrites the cache has to get
 * the new value exactly right, and two concurrent writers can interleave and
 * leave a value that never existed in the database. Deleting the key costs one
 * slow read and cannot be wrong.
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final String KEY_ALL = "tasks:all";
    private static final String KEY_ONE_PREFIX = "task:";

    private static final String SOURCE_CACHE = "ElastiCache Redis";
    private static final String SOURCE_DB = "PostgreSQL via RDS Proxy";

    private final TaskRepository repository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public TaskService(TaskRepository repository,
                       StringRedisTemplate redis,
                       ObjectMapper objectMapper,
                       @Value("${app.cache.ttl-seconds}") long ttlSeconds) {
        this.repository = repository;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public ReadResult<List<TaskDto>> listTasks() {
        long start = System.nanoTime();

        String cached = readFromCache(KEY_ALL);
        if (cached != null) {
            try {
                List<TaskDto> items = objectMapper.readValue(cached, new TypeReference<List<TaskDto>>() {
                });
                return new ReadResult<>(items, true, SOURCE_CACHE, millisSince(start));
            } catch (Exception e) {
                // A value we cannot parse is a value from an older version of
                // this code. Drop it and fall through to the database.
                log.warn("Discarding unparseable cache entry for {}: {}", KEY_ALL, e.getMessage());
                deleteFromCache(KEY_ALL);
            }
        }

        List<TaskDto> items = repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(TaskDto::from)
                .toList();
        writeToCache(KEY_ALL, items);
        return new ReadResult<>(items, false, SOURCE_DB, millisSince(start));
    }

    public Optional<ReadResult<TaskDto>> getTask(Long id) {
        long start = System.nanoTime();
        String key = KEY_ONE_PREFIX + id;

        String cached = readFromCache(key);
        if (cached != null) {
            try {
                TaskDto dto = objectMapper.readValue(cached, TaskDto.class);
                return Optional.of(new ReadResult<>(dto, true, SOURCE_CACHE, millisSince(start)));
            } catch (Exception e) {
                log.warn("Discarding unparseable cache entry for {}: {}", key, e.getMessage());
                deleteFromCache(key);
            }
        }

        return repository.findById(id)
                .map(TaskDto::from)
                .map(dto -> {
                    writeToCache(key, dto);
                    return new ReadResult<>(dto, false, SOURCE_DB, millisSince(start));
                });
    }

    @Transactional
    public TaskDto create(String title) {
        Task saved = repository.save(new Task(title));
        // Only the list key exists yet for a brand new row.
        invalidate(KEY_ALL);
        return TaskDto.from(saved);
    }

    @Transactional
    public Optional<TaskDto> update(Long id, String title, Boolean completed) {
        return repository.findById(id).map(task -> {
            if (title != null && !title.isBlank()) {
                task.setTitle(title);
            }
            if (completed != null) {
                task.setCompleted(completed);
            }
            Task saved = repository.save(task);
            invalidate(KEY_ALL, KEY_ONE_PREFIX + id);
            return TaskDto.from(saved);
        });
    }

    @Transactional
    public boolean delete(Long id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        invalidate(KEY_ALL, KEY_ONE_PREFIX + id);
        return true;
    }

    // -------------------------------------------------------------------------
    // Redis access. Every call is wrapped: a cache that is down should make the
    // application slower, never broken. A failed read falls through to
    // PostgreSQL and a failed write is dropped, both with a log line.
    // -------------------------------------------------------------------------

    private String readFromCache(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("Cache read failed for {} - serving from the database: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeToCache(String key, Object value) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Cache write failed for {}: {}", key, e.getMessage());
        }
    }

    private void deleteFromCache(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Cache delete failed for {}: {}", key, e.getMessage());
        }
    }

    private void invalidate(String... keys) {
        for (String key : keys) {
            deleteFromCache(key);
        }
    }

    /**
     * Fractional milliseconds on purpose: a Redis hit on the same VPC lands
     * well under a millisecond, and reporting it as a flat 0 ms makes the
     * demo look broken rather than fast.
     */
    private static double millisSince(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 1_000.0) / 1_000.0;
    }
}
