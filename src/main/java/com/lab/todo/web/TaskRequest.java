package com.lab.todo.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must be 200 characters or fewer")
        String title,
        Boolean completed) {
}
