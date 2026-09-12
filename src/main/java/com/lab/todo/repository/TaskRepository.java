package com.lab.todo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lab.todo.model.Task;

/**
 * Every query here travels to PostgreSQL through the RDS Proxy - the JDBC URL
 * is built from DB_HOST, which the task definition sets to the proxy endpoint.
 * The instance endpoint is not reachable from the app subnets at all.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findAllByOrderByCreatedAtDesc();
}
