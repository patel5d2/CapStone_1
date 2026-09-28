# CampusBridge Design Document

## Read before doing anything

Before making code changes, architecture decisions, or scope assumptions, read the project agent guidance in [context/AGENTS.md](../AGENTS.md) and the supporting design context in the order it specifies:

1. [context/1_overview.md](../1_overview.md)
2. [context/2_architecture.md](../2_architecture.md)
3. [context/3_patterns.md](../3_patterns.md)
4. [context/4_ui_design.md](../4_ui_design.md)
5. [context/5_progress.md](../5_progress.md)
6. [context/6_rules.md](../6_rules.md)

This document describes the current implementation and the intended product vision. It distinguishes between what is already built, what is partially implemented, and what is still planned or proposed so that requirements remain honest and traceable.

---

## 1. Project purpose

CampusBridge is a regional campus marketplace for the Cincinnati metro area, designed to connect students across nearby institutions while providing community features that give the platform value between transactions.

The product goal is not merely resale; it is local density, trust, and engagement. A student should be able to browse nearby listings, discover peers, communicate safely, and access support resources without exposing personal contact details.

The core product thesis is to create a cross-campus marketplace that works better than a single-school marketplace because it combines many adjacent schools into one trusted network.

---

## 2. Current project reality

The repository already contains a working backend and frontend foundation, but it does not yet fully satisfy the full senior design contract. The current app is best described as a functional prototype / early-marketplace foundation rather than a completed production-ready platform.

### 2.1 Backend implementation

The backend is a Spring Boot 4.1.1 application using Java 21 and Maven.

Core technology in the repo:

- Spring Boot WebMVC
- Spring Data JPA and Hibernate
- Spring Security with Clerk JWT validation
- OAuth2 resource server for token verification
- PostgreSQL runtime dependency and H2 development database
- Spring Actuator with Prometheus metrics
- Lombok, validation, and JPA-based domain models

The codebase includes modules for:

- marketplace functionality
- community features
- messages and block/report flows
- support resources
- school data
- student/user related APIs
- security and auth

A large portion of the architecture already reflects the intended marketplace domain, and the app is structured in a modular monolith style.

### 2.2 Frontend implementation

The frontend is a React 19 + TypeScript + Vite application with:

- React Router 7
- Tailwind CSS 4
- Clerk authentication integration
- marketplace, messages, community, support, directory, profile pages
- a shell layout and protected routes
- token bridge logic that supplies fresh Clerk tokens to the API

The app has a SPA shell and routes already defined in [frontend/src/App.tsx](../../frontend/src/App.tsx). The user experience is designed around multi-tab navigation and protected authenticated pages.

### 2.3 What is present today

The project currently includes:

- authenticated Clerk-backed API access
- a React SPA served from the backend
- marketplace CRUD-style domain objects and controllers
- community entities including posts, comments, groups, and events
- support resource endpoints
- a directory-oriented student browsing flow
- messaging and user report support infrastructure
- Docker and Compose configuration for app + Postgres + monitoring
- CI and release-related project setup
- JaCoCo reporting and backend test setup

### 2.4 What is still incomplete or unverified

The repository still has important gaps relative to the intended final product:

- PostgreSQL startup is currently blocked by schema initialization issues
- the database initialization scripts are not mounted correctly in the Compose path
- Flyway baseline migration is planned but not yet implemented
- the app still relies on H2-like development configuration despite a PostgreSQL target architecture
- the email-based ownership model is still used in places and is planned to be migrated to Clerk user IDs
- institutional-domain enforcement is not adequately enforced
- 2FA enablement and allowlist enforcement are not fully implemented in deployment config
- image upload/storage is still planned
- school theming is not fully automatic on login
- moderation/admin dashboards are not complete
- admin authorization and action model are not fully in place
- full legal, security, and operational verification remains incomplete

---

## 3. Intended product design

The intended product is a verified regional marketplace and student community platform for the Cincinnati metro area.

### 3.1 Primary user experience

Students should be able to:

- sign in with a verified institutional email through Clerk
- create and manage listings with photos, categories, and pricing
- search and filter listings by school, category, price, and condition
- message sellers securely without exposing personal contact details
- browse a campus directory and community discussion areas
- find support resources by school
- report inappropriate listings or users
- access a consistent school-themed experience

### 3.2 Intended navigation

The design calls for four primary tabs:

1. Marketplace
2. Messages
3. Community
4. Support

The planned user experience keeps the student directory inside the Community area rather than as a separate top-level destination.

### 3.3 Intended roles

The product is planned around three main roles:

- Visitor: anonymous user who can view landing pages and authentication flows
- Student: admitted verified student with access to marketplace and community surfaces
- Admin: authority to review reports, manage listings, and moderate accounts/schools

### 3.4 Planned business and social value

The design intentionally mixes marketplace and community functions. Engagement features are not optional extras; they are part of the system's retention model. Students return to the platform for community and support in addition to transactions.

---

## 4. Functional scope: built vs target

### 4.1 Built or partially built

The following features are present in the repo or clearly evidenced in the codebase:

- user authentication via Clerk
- Spring Boot API endpoints for marketplace and community flows
- account/profile-oriented backend
- listing and support resource domain objects
- school and community-related entity models
- frontend SPA navigation shell
- Clerk token injection for authenticated API requests
- Docker and monitoring configuration
- basic health/metrics exposure

### 4.2 Planned or intended but not yet delivered

The following are central to the product vision but are still not fully implemented or verified:

- institutional email allowlist enforcement
- 2FA enforcement and verification policy
- school auto-theming on login
- image upload infrastructure
- real-time messaging and inbox behavior
- admin moderation queue and actions
- domain-based student identity keying with Clerk user ID
- migrations and production-safe database initialization
- full security validation against OWASP concerns
- accessibility validation for every theme and flow
- deployment reliability and uptime validation

### 4.3 Proposed additions requiring scope decision

These are described in the project context as proposals rather than signed completed requirements:

- offers
- buyer/seller reviews
- purchase history
- peer mentorship
- course study groups
- campus map / meetup spots
- notification center
- home feed

These features may be desirable and may appear in roadmap planning, but they should not be treated as confirmed requirements until recorded team approval is in place.

---

## 5. Architecture summary

The application follows a modular monolith architecture:

- one Spring Boot application
- one React SPA frontend
- backend handles authentication, API routes, and business logic
- frontend is served from backend static resources
- PostgreSQL is the intended production database
- Stripe or payments are explicitly not in scope
- the design avoids microservices for this project size and timeline

The current architecture is intended to support later growth while staying lightweight enough for the project team to deliver successfully.

---

## 6. Data and identity model

### 6.1 Identity direction

The project intends to key identity on the Clerk user ID instead of email because emails are mutable and can orphan records if changed.

This is a major requirement for the planned future state, and the current code still contains legacy email-based ownership patterns that need migration.

### 6.2 School mapping

The product intends to support multiple institutions, with the target school list defined in the project context. The system should map verified institutional emails to schools and permit students to create listings and participate in community features based on that classification.

### 6.3 Privacy and safety

The design requires privacy controls and safe communication patterns:

- personal contact information must never be exposed publicly
- communication should remain in-platform
- field-level privacy controls are desired
- blocking and reporting flows are planned
- moderation actions must be centralized and report-driven

---

## 7. Security and compliance goals

This project is designed with security and ethics as explicit requirements.

The intended compliance/security posture includes:

- verified institutional email access only
- 2FA for student accounts
- no high-severity OWASP Top Ten findings at final demo
- no passwords stored in the application itself because Clerk handles credential management
- TLS termination at the deployment boundary
- WCAG 2.1 AA accessibility support
- FERPA-aware minimization of personal data
- breach notification and operational review considerations

The security requirements are concrete goals rather than optional enhancements.

---

## 8. Current outstanding risks

The main risks to delivery are:

- database startup failing in Docker/Postgres setup
- incomplete schema initialization and migration strategy
- identity model mismatch between legacy email ownership and intended Clerk user ID model
- lack of deployed verification for 2FA and institutional-domain enforcement
- missing image storage and moderation workflows
- incomplete monitoring and reliability evidence
- open questions around deployment, school list, and feature scope

These are not minor issues; they are the primary blockers to treating the system as production-ready.

---

## 9. Summary of intended final state

The final CampusBridge product should be a verified, regional, trusted student marketplace with community features that keep students engaged beyond transactions.

The final product should include:

- verified institutional login
- school-aware listing and community flows
- secure in-platform communication
- moderation and admin management
- regional search and discovery
- student directory and community engagement
- support resources by institution
- reliable operational infrastructure and monitoring

The repository currently has the foundation for this system, but the final state still requires substantial completion of the project requirements, testing, and deployment readiness.

---

## 10. Design conclusion

This project is best understood as an intentionally ambitious student marketplace and community platform whose core architecture is in place, but whose final delivery still depends on completing the foundational operational, identity, moderation, and deployment work.

The most important point is that the app is not complete simply because the code and frontend structure exist. The design must remain accurate to the current state while respecting the signed project intent and the roadmap already captured in the project context.

The source of authority for the product direction remains the documented requirements and sprint context in [context/1_overview.md](../1_overview.md), [context/2_architecture.md](../2_architecture.md), and [context/5_progress.md](../5_progress.md). The design here is intended to track both what exists and what the project is still trying to become.
