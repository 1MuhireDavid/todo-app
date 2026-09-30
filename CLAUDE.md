# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Authorship

Never add AI attribution anywhere. No `Co-Authored-By: Claude`, no "Generated
with Claude Code", no "written by Claude", in commit messages, PR titles or
bodies, file headers, or code comments. Commits and pull requests are authored by
the repository owner alone.

## Comments

The default is no comment. Add one only when a reader who knows Java and Spring
would still ask "why".

- **Explain why, never what.** If a comment restates the line below it, delete it.
- **Never interrupt a block.** No comments between the fields of a class, between
  the properties in `application.properties`, or between the steps of a
  workflow's `run:` script. Put the rationale above the method, the class, or
  the step.
- **One comment per method, at most, and two lines at most.** Anything longer
  belongs in the README.
- **No section-divider banners.** No `// ----- Redis access -----` blocks.
- **Javadoc only where the contract is non-obvious.** A class whose name says
  what it does does not need a paragraph saying it again. No `@param` or
  `@return` that repeats the signature.
- **No commented-out code.** Delete it; git remembers.

Bad:

```java
public ReadResult<List<TaskDto>> listTasks() {
    // start the timer
    long start = System.nanoTime();
    // look in redis first
    String cached = readFromCache(KEY_ALL);
    if (cached != null) {
        // cache hit, deserialize and return
        ...
```

Good:

```java
public ReadResult<List<TaskDto>> listTasks() {
    long start = System.nanoTime();
    String cached = readFromCache(KEY_ALL);
    ...
```

## Configuration

Every value comes from the environment and nothing has a fallback — a missing
variable must fail the container, not start it against the wrong backend. New
required variables go in `EnvironmentValidator.REQUIRED`, in
`application.properties` without a default, and in the task definition in
`todo-app-infra` (see below).

Credentials go in the task definition's `secrets` block with a `ValueFrom` key
selector. Never in `environment`, never in a properties file, never in this
repository.

## Keeping in step with the infrastructure

The task definition lives in `todo-app-infra`: the bootstrap revision in
`06-alb-ecs.yaml` and the live one in the `RenderProject` buildspec in
`07-cicd-pipeline.yaml`, because CloudFormation cannot update a
`CODE_DEPLOY`-controlled service. Change the container shape in both.

## Before proposing a change as done

`mvn -B -ntp clean package` must pass.
