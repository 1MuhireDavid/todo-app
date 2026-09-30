package com.lab.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.lab.todo.config.EnvironmentValidator;

@SpringBootApplication
public class TodoApplication {

    public static void main(String[] args) {
        // Checked before Spring starts, so a missing variable produces one
        // readable message in the task log instead of a placeholder resolution
        // failure buried in a stack trace - and the task exits immediately
        // rather than sitting in a crash loop the ALB slowly marks unhealthy.
        EnvironmentValidator.validateOrExit(System.getenv());
        SpringApplication.run(TodoApplication.class, args);
    }
}
