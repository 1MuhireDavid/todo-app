package com.lab.todo.web;

/**
 * A read plus the two facts the UI puts on screen: whether Redis served it, and
 * how long it took. The whole point of the caching requirement is being able to
 * see the difference, so the evidence travels with the payload rather than
 * hiding in a log line.
 */
public record ReadResult<T>(T data, boolean cacheHit, String source, double elapsedMs) {
}
