package com.lab.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.lab.todo.config.EnvironmentValidator;

@SpringBootApplication
public class TodoApplication {

    public static void main(String[] args) {
        EnvironmentValidator.validateOrExit(System.getenv());
        SpringApplication.run(TodoApplication.class, args);
    }
}
