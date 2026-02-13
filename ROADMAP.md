# LumaTrace – Project Roadmap

This document defines the evolution phases of the LumaTrace project, from its current state to a stable and extensible version. It focuses on *what* is being built and the order of execution.

## Current Status (Baseline)
✔️ Functional multi-module Maven project.
✔️ Reproducible build (`mvn clean install`).
✔️ Clear separation of concerns:
- `lumatrace-core`: Pure algorithmic engine.
- `lumatrace-cloud`: Spring Boot REST API.
  ✔️ Basic CI via GitHub Actions.
  ✔️ Functional Dockerfile.
  ✔️ Minimum Viable API (Photo registration).

**Status: Foundation Complete**

---

## Phase 1 – Core Stabilization (v0.1)
**Goal:** Turn `lumatrace-core` into a solid, predictable library.
- **Tasks:**
    - Deterministic unit testing for the algorithm.
    - Reproducible statistical validation (fixed seed).
    - Clear decoupling: Signature Generation vs. Insertion vs. Detection.
    - Document public Core API.
    - Freeze public interfaces (`@PublicAPI`).
- **Expected Outcome:** Core usable as a standalone library with stable behavior across versions.

## Phase 2 – Coherent Cloud API (v0.2)
**Goal:** Make `lumatrace-cloud` an extensible and clear API.
- **Tasks:**
    - Explicit DTOs (decoupling from JPA entities).
    - Uniform error handling (Problem Details / RFC 7807).
    - Exhaustive input validation.
    - Healthcheck implementation (`/actuator/health`).
    - API Versioning (`/api/v1`).
- **Expected Outcome:** API ready for external integration with clear contracts for mobile/web clients.

## Phase 3 – Persistence & Traceability (v0.3)
**Goal:** Establish solid minimum traceability.
- **Tasks:**
    - Final domain model (Photo, Registration, Provenance).
    - Database indexes and constraints.
    - Reproducible canonical hashing.
    - Basic auditing (timestamps, source tracking).
- **Expected Outcome:** Reliable event logging and context reconstruction capabilities (C2PA/JUMBF readiness).

## Phase 4 – Algorithmic Robustness (v0.4)
**Goal:** Increase resilience without sacrificing simplicity.
- **Tasks:**
    - Real multi-scale detection.
    - Tolerance for slight rotation.
    - Automated confidence metrics.
    - Internal testing dataset.
- **Expected Outcome:** Stable detection in real-world scenarios with quantifiable metrics.

## Phase 5 – Integration & Deployment (v1.0)
**Goal:** End-to-end usable system.
- **Tasks:**
    - Docker Compose orchestration (API + DB).
    - Documented environment variables.
    - Basic security hardening (secrets, profiles).
    - Final README alignment.
    - Tag `v1.0.0`.
- **Expected Outcome:** Deployable project in local and cloud environments with professional documentation.

---

## Out of Scope (Current Focus)
The following are explicitly NOT addressed in this stage:
- DRM (Digital Rights Management).
- Aggressive obfuscation.
- Active watermark removal.
- AI adversarial warfare.
- Cryptographic authority management.

## Final Note
LumaTrace does not aim for "perfection," but for utility, honesty, and robustness within a realistic threat model.