# todo-app

Spring Boot To-Do API for the ECS Fargate lab. Writes go to PostgreSQL through
**RDS Proxy**; reads are cached in **ElastiCache for Redis** with a read-through
pattern, and every read reports whether it was a cache hit and how long it took.

Infrastructure lives in the separate
[`todo-app-infra`](https://github.com/1MuhireDavid/todo-app-infra) repository.
This repository contains no CloudFormation, and that one contains no application
code.

## Layout

```
src/main/java/com/lab/todo/
  TodoApplication.java              validates the environment, then boots
  config/EnvironmentValidator.java  fails fast and loudly on missing config
  model/Task.java
  repository/TaskRepository.java
  service/TaskService.java          read-through cache, invalidate on write
  web/                              controllers and wire types
src/main/resources/
  application.properties            every value from the environment, no defaults
  static/index.html                 UI with the cache hit/miss indicator
Dockerfile                          multi-stage, non-root runtime
.github/workflows/build-and-push.yml
```

## API

| Method | Path | Notes |
|---|---|---|
| `GET` | `/health` | `{"status":"UP"}`. Touches nothing external — see below. |
| `GET` | `/api/tasks` | Read-through. Returns `{data, cacheHit, source, elapsedMs}`. |
| `GET` | `/api/tasks/{id}` | Same shape, cached under `task:<id>`. |
| `POST` | `/api/tasks` | `{"title": "..."}` → 201. Invalidates `tasks:all`. |
| `PUT` | `/api/tasks/{id}` | `{"title": "...", "completed": true}`. Invalidates both keys. |
| `DELETE` | `/api/tasks/{id}` | 204. Invalidates both keys. |

## Caching

Keys are `tasks:all` and `task:<id>`, both with a TTL of `CACHE_TTL_SECONDS`
(60 by default).

A read looks in Redis, falls back to PostgreSQL on a miss, and writes what it
found back under the TTL. A write goes to PostgreSQL through the proxy and then
**deletes** the affected keys rather than rewriting them — a write that rewrites
the cache has to reproduce the new value exactly, and two concurrent writers can
interleave and leave a value the database never held. Deleting costs one slow
read and cannot be wrong.

Every Redis call is wrapped. A cache that is down makes the application slower,
never broken: a failed read falls through to PostgreSQL, a failed write is
dropped, and both leave a log line.

The response carries `cacheHit`, `source` and `elapsedMs` because the whole
point of the requirement is being able to *see* the difference. Open the page,
click **Read again**, and the badge flips from `CACHE MISS` to `CACHE HIT` with
the time dropping by an order of magnitude. Add a task and it flips back.

## Why `/health` checks nothing

It is what the ALB polls to decide whether a task keeps receiving traffic. If it
checked PostgreSQL, one RDS failover or proxy reconnection would fail every
task's health check simultaneously, the ALB would drain all of them, and a
recoverable blip would become an outage. A dependency check belongs on a
separate endpoint that alarms without gating traffic.

The trade is real: a task whose database is gone stays in the target group and
serves 500s. That is the better failure mode of the two.

## Configuration

Everything comes from the environment, and nothing has a fallback. `SERVER_PORT`,
`DB_HOST`, `DB_PORT`, `DB_NAME`, `REDIS_HOST`, `REDIS_PORT` and
`CACHE_TTL_SECONDS` come from the task definition's `environment` block;
`DB_USERNAME`, `DB_PASSWORD` and `REDIS_AUTH_TOKEN` come from its `secrets`
block, which ECS resolves from Secrets Manager before the container starts. No
credential is ever in an environment variable in the template, in this
repository, or in `describe-task-definition` output.

`EnvironmentValidator` checks the whole list before Spring starts and exits with
every missing name in one message, so a typo costs one restart rather than
several.

`DB_HOST` is the **RDS Proxy** endpoint. The instance endpoint is not reachable
from the app subnets at all — the instance security group admits only the proxy.

## Build and run locally

```bash
mvn -B -ntp clean package

# Needs a PostgreSQL and a Redis to point at; the TLS settings below assume
# ElastiCache, so a local Redis needs spring.data.redis.ssl.enabled=false.
SERVER_PORT=8080 \
DB_HOST=localhost DB_PORT=5432 DB_NAME=todoapp \
DB_USERNAME=postgres DB_PASSWORD=postgres \
REDIS_HOST=localhost REDIS_PORT=6379 REDIS_AUTH_TOKEN=devtoken \
java -jar target/todo-app.jar
```

```bash
docker build -t todo-app:dev .
```

The image is multi-stage — the Maven toolchain and full JDK stay in the build
layer — and the runtime layer runs as the non-root `app` user on a JRE. Heap is
sized with `-XX:MaxRAMPercentage=75.0` rather than a fixed `-Xmx`, so changing
`TaskMemory` in CloudFormation does not require rebuilding the image.

## Deployment

`build-and-push.yml` runs on a push to `main` and does two things: build the
image, then push it as `sha-<short12>` and as `latest`. It never reads the
infrastructure stack.

The push of `latest` fires an EventBridge rule that starts the pipeline in
`todo-app-infra`. There, a CodeBuild step writes `taskdef.json` and
`appspec.yaml` from values CloudFormation passes it, and CodeDeploy runs the
blue/green deploy with the new image.

On the very first run the stack does not exist yet, so nothing is listening and
the push only makes `latest` available for the stack to be created with.

**Rolling back** does not need a rebuild. Re-tag a known-good `sha-` image as
`latest` and push it:

```bash
MANIFEST=$(aws ecr batch-get-image --repository-name todo-app --region us-east-1 \
  --image-ids imageTag=sha-<known-good> --query 'images[0].imageManifest' --output text)
aws ecr put-image --repository-name todo-app --region us-east-1 \
  --image-tag latest --image-manifest "$MANIFEST"
```

Within the CodeDeploy termination wait window (10 minutes after a cutover), a
faster option is shifting traffic back to the blue task set from the CodeDeploy
console — no deploy required.

## Keeping this in step with the infrastructure

The task definition lives in `todo-app-infra`, in two places: `06-alb-ecs.yaml`
holds the *bootstrap* revision the service is created with, and the `RenderProject`
buildspec in `07-cicd-pipeline.yaml` produces every revision after it.
CloudFormation cannot update `TaskDefinition` on a `CODE_DEPLOY` service, which
is why the second copy exists.

If you change the container shape — a new environment variable, a different port,
another secret — change it in **both** templates.
