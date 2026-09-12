package com.lab.todo.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness only. This deliberately touches neither PostgreSQL nor Redis.
 *
 * <p>The ALB uses this endpoint to decide whether to keep a task in the target
 * group. If it checked the database, a brief RDS failover or a proxy
 * reconnection would make every task fail its health check at the same moment,
 * the ALB would drain all of them, and a recoverable blip would become an
 * outage. A dependency check belongs on a separate endpoint that a dashboard
 * polls, not on the one that controls traffic.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
