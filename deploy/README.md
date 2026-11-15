# Deployment

## Docker Compose — use this today

Four profiles, one base file (`compose/docker-compose.yml`) layered with a
profile override. Every profile was built and health-checked live in this
repo's QA session (see `../qa/test-execution-report.md`).

```bash
cd deploy/compose
cp .env.example .env   # fill in a real PIE_DB_PASSWORD
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
curl localhost:8080/actuator/health
```

Swap `dev` for `test`, `integration`, or `paper`. `paper` runs the exact
risk numbers production would use (see `application-paper.yml` in the Java
module) — it's the one to actually leave running while you validate the
strategy.

## Kubernetes — readiness, not yet deployment

`k8s/risk-engine/` has a complete manifest set (Namespace, ConfigMap,
Secret template, Deployment, Service, HPA, PodDisruptionBudget,
NetworkPolicy) written to the owner's own Phase 2/3 spec: stateless pods,
startup/liveness/readiness probes wired to Spring's actuator, resource
requests/limits, a non-root security context, Prometheus scrape
annotations, and a default-deny NetworkPolicy with explicit allows.

**These have been schema-validated (every doc parses and has
`apiVersion`/`kind`/`metadata`) but never applied to a real cluster** — no
Kubernetes cluster exists in the environment these were written in. Before
using them:

1. Build and push a real image tag (replace `REPLACE_WITH_CI_BUILT_TAG` in
   `deployment.yaml` — never `:latest` in production).
2. Replace `secret.example.yaml` with real secrets from your actual secret
   manager, never a committed file.
3. Point `PIE_DB_HOST`/`PIE_REDIS_HOST` in `configmap.yaml` at your real
   managed Postgres/Redis (or your own in-cluster services).
4. `kubectl apply -f k8s/risk-engine/` and watch the rollout — the first
   real cluster test these manifests get.

This matches the owner's own stated decision: "Docker Compose for initial
development, testing and paper trading. Kubernetes for production when
multi-node availability, independent scaling and operational requirements
justify the additional complexity." That bar hasn't been reached yet.
