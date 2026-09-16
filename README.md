# Enterprise Dev Group Project - Design Document

[![CI](https://github.com/patel5d2/CapStone_1/actions/workflows/main.yml/badge.svg)](https://github.com/patel5d2/CapStone_1/actions/workflows/main.yml)

Current release: `v0.1.1`

## 1. Introduction

The application created in this problem is a University Student Directory program intended for higher learning institutions within the tri-state region. These include institutions such as Xavier University, the University of Cincinnati (UC), as well as Cincinnati State. The program solves the problem of the lack of centralization of information concerning student enrollment within the institutions within the specified regions

- Users: University administrators and students within the tri-state network.
- Main Purpose: To maintain a searchable record of students’ first and last names, their associated IDs, and their respective universities
---

## 2. Storyboard
![Create Account Screen](screen-1.png)

![Verify Student Screen](screen-2.png)

![Search Students Screen](screen.png)

---


## 3. Functional Requirements

### Requirement 1: Student Search

**User Story:**
- **As a** student/administrator
- **I want** to be able to search for other students in the tri-state area
- **So that I can** connect with them via email

**Acceptance Criteria:**

#### Scenario 1: Successful Search
- **Given** that I've logged in
- **When** I search for students by a particular field (e.g., Major, First Name, etc.)
- **Then** I should see students populate on the page

#### Scenario 2: Unsuccessful Search
- **Given** that I've logged in
- **When** I search for students by a particular field (e.g., Major, First Name, etc.) that doesn't exist in the database
- **Then** I should see a message telling me no students were found

### Requirement 2: Easy Navigation

**User Story:**
- **As a** student/administrator
- **I want** to be able to easily navigate through the web application
- **So that I** know where to go for certain functions (Searching, Updating Profile)

**Acceptance Criteria:**

#### Scenario 1: Finding Profile Update Option
- **Given** that I'm logged in and I'm looking to update my profile
- **When** I look for where I can edit/update my profile
- **Then** I should easily find the tab/option to update my profile

### Requirement 3: Partial Search Capability

**User Story:**
- **As a** student/administrator user
- **I want** to be able to do partial searches on students based on particular fields
- **So that** if I don't have specific information on a student I want to look up, I can search through the possible matches

**Acceptance Criteria:**

#### Scenario 1: Partial Search with Multiple Criteria
- **Given** that I'm logged in and in the searching tab/option
- **When** I try to search for students whose names start with the letter 'A' and attend Xavier University
- **Then** I should see students that fit this description on the page

#### Scenario 2: Partial Search with No Matches
- **Given** that I'm logged in and in the searching tab/option
- **When** I try to search for students whose names start with the letter 'Z' and attend St. Mary's University
- **Then** I should see a message telling me no students were found

### Requirement 4: Complete Profile

**User Story:**
- **As a** student/administrator
- **I want** to be able to complete my profile
- **So that** people are able to reach me and see my university-related information

**Acceptance Criteria:**

#### Scenario 1: Account Creation with Field Requirements
- **Given** I don't have an account yet
- **When** I try creating one with all required fields filled in
- **Then** I should my account created successfully

#### Scenario 2: Attempting to submit with missing required fields
- **Given** I don't have an account yet
- **When** I try creating one with missing required fields
- **Then** I should get a message telling me to fill in the required fields to create my account

### Requirement 5: Input Validation

**User Story:**
- **As a** student/administrator
- **I want** to be able to know if my search input is valid input prior to searching
- **So that I'm** able to get results back

**Acceptance Criteria:**

#### Scenario 2: Valid Input
- **Given** that I'm logged in and in the searching tab/option
- **When** I try to search for students with alphanumeric characters
- **Then** I should be able to proceed with my search submission

#### Scenario 2: Invalid Input
- **Given** that I'm logged in and in the searching tab/option
- **When** I try to search for students but type a non-alphanumeric character in a field
- **Then** I should get a warning that my input is invalid and needs to be corrected before submission

---

## 4. Class Diagram

![UML Diagram](classUMLDiagram.png)

### Class Diagram Description

- **User**: Represents application users with authentication credentials and profile information. Implements UserDetails interface for Spring Security.
- **Task**: Main entity representing a work item with title, description, status, priority, and due date. Associated with User (assignee).
- **Project**: Groups related tasks together. Contains multiple tasks and team members.
- **TaskRepository**: JPA repository interface for CRUD operations on Task entities. Extends JpaRepository.
- **UserRepository**: JPA repository interface for user data access. Provides custom query methods for finding users by email and username.
- **TaskService**: Business logic layer for task management. Handles task creation, assignment, and status updates.
- **TaskController**: REST controller exposing task-related endpoints. Maps HTTP requests to service methods.
- **TaskDTO**: Data Transfer Object for task information sent to/from the API. Separates internal model from API representation.



- [ Student: Represents the core entity with attributes for firstName, lastName, studentID, and universityName.
  • University: Represents the tri-state institutions (e.g., Xavier, UC).
  • StudentRepository: JPA repository interface for CRUD operations on student records.
  • StudentService: Business logic layer handling directory searches and data validation.
  • StudentController: REST controller managing JSON-based API requests for student data]
---

## 5. JSON Schema
```
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Student",
  "type": "object",
  "properties": {
    "studentID": { "type": "integer" },
    "firstName": { "type": "string" },
    "lastName": { "type": "string" },
    "major": { "type": "string" },
    "universityName": { "type": "string" },
    "grade": { "type": "string" },
    "contact": {
      "type": "object",
      "properties": {
        "residentCity": { "type": "string" },
        "residentState": { "type": "string" }
      }
    }
  },
  "required": ["studentID", "firstName", "lastName", "universityName"]
}
```
---

## 6. Scrum Roles

Five members:

- **Scrum Master and Developer**: Jonatan Soriano Sanjuan — facilitates scrum ceremonies, removes impediments, and develops
- **DevOps and QA**: Dharmin Patel — build pipeline, containers, releases, and test strategy
- **Developer**: Matthew Brown
- **Developer and UI/UX**: Shamak Patel — interface design and front-end development
- **Security**: Jessica Pham — OWASP review, authentication and authorization, dependency and container scanning


---

## 7. GitHub Repository

**Repository Link**: ```https://github.com/jonathansoriano/EnterpriseDevGroupProject```

---

## 8. Project Board & Milestones

**Project Board Link**: ```https://github.com/users/patel5d2/projects/2```

**Weekly Milestones**: ```https://github.com/jonathansoriano/EnterpriseDevGroupProject/milestones```


### How to Run the Project

The app is a React + TypeScript single-page app served by a Spring Boot API.

**Day-to-day development** — two terminals, with hot reload on the frontend:

```bash
# Terminal 1 — API on http://localhost:8080
./mvnw spring-boot:run

# Terminal 2 — SPA on http://localhost:5173 (proxies /api and /student to 8080)
cd frontend
cp .env.example .env     # first time only
npm install              # first time only
npm run dev
```

**Single-server run** — build the SPA into the backend's static resources, then
run just Spring Boot on http://localhost:8080:

```bash
cd frontend && npm install && npm run build && cd ..
./mvnw spring-boot:run
```

The compiled bundle is generated, not committed, so `npm run build` is required
at least once before the single-server run serves any UI. The Docker image runs
that build itself in a dedicated stage.

---

## 9. Weekly Standup Meeting

**Meeting Time**: Every Monday at 05:00PM EST  
**Platform**: Microsoft Teams  
**Meeting Link**: ``` https://teams.microsoft.com/meet/2727813537852?p=8FKd43Pa4OstTky9WE```

**Meeting Agenda**:
- What did you accomplish this week?
- What are you working on next?
- Are there any blockers or issues?

---
## 10. Deployment option
#### Option 1: Local Development with Docker Compose (Builds from source)
# Clone the repo
```bash
# Clone the repo
git clone https://github.com/jonathansoriano/EnterpriseDevGroupProject.git
cd EnterpriseDevGroupProject

# Create .env file with required variables
cp .env.example .env
# Edit .env with your database credentials

# Start all services (builds the app image locally)
docker-compose up -d

# Access services:
# App: http://localhost:8080
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000
```
#### Option 2: Production with Pre-built Registry Image
```bash
# Run just the app from this repository's GHCR package
docker run -p 8080:8080 ghcr.io/patel5d2/capstone_1:latest
```

### Releases and packages

Version tags in the form `vX.Y.Z` run the release workflow. A successful run:

- publishes `ghcr.io/patel5d2/capstone_1` for Linux AMD64 and ARM64 with version, major/minor, SHA, and `latest` tags;
- creates a GitHub release with the executable JAR, CycloneDX image SBOM, and SHA-256 checksums;
- records build provenance for both the JAR and container image.

The version tag must match the non-SNAPSHOT version in `pom.xml`.
---

## CampusBridge App

The frontend is a React 19 + TypeScript SPA (Vite, Tailwind v4, Clerk) living in
[`frontend/`](frontend/README.md). It ships as part of the same JAR and container
as the API: `npm run build` writes the bundle into `src/main/resources/static`,
and Spring forwards client-side routes to `index.html`
(`config/SpaForwardingConfig.java`). The layout is a desktop header nav plus a
native-style bottom tab bar on phones, so the planned React Native app can reuse
the same navigation model and API contracts.

Beyond the student directory (search & profile), the app includes four more
tabs, all authenticated with the same Clerk session as the rest of the site:

- **Marketplace** (`/marketplace.html`) — create, search, favorite, and report
  listings (sell, rent, free/donate, looking-for), with a My Listings /
  Favorites view and mark-as-sold.
- **Messages** (`/messages.html`) — direct and per-listing conversations,
  block/report users. Polls for new messages; real-time delivery via
  WebSockets is planned for a later sprint.
- **Community** (`/community.html`) — a general post feed (like/comment),
  groups (major, graduation year, or course study group), and an events
  board.
- **Support** (`/support.html`) — each school's food pantry, emergency aid,
  and counseling contacts, plus anonymous help requests that any student can
  offer to fulfill.

All of it is backed by new REST endpoints under `/api/marketplace`,
`/api/messages`, `/api/community`, and `/api/support` (see the corresponding
packages under `src/main/java/.../{marketplace,messages,community,support}`).
Their tables are plain JPA entities managed by `spring.jpa.hibernate.ddl-auto:
update`, kept separate from the hand-written `university` / `student` /
`app_user` tables so the existing directory and Clerk auth are untouched.
Known follow-ups: real-time chat over WebSockets, image upload to
Cloudinary/S3 (listings currently take a plain photo URL), and the campus map.

## Technology Stack

- **Backend**: Spring Boot, Spring Data JPA
- **Database**: PostgreSQL / H2 (for development)
- **Frontend**: React 19 + TypeScript, Vite, Tailwind CSS v4, React Router
- **Authentication**: Clerk (`@clerk/clerk-react` in the SPA, JWT validation in the API)
- **Testing**: JUnit, Mockito, Spring Test
- **Build Tool**: Maven (backend), npm/Vite (frontend)
- **Version Control**: Git/GitHub
- **CI/CD**: GitHub Actions
- **Deployment**: [AWS / Heroku / Azure / Other]

---

## Development Guidelines
- All methods must include JavaDocs to explain their functionality. 
- Pull Requests must document major breaking changes and confirm unit testing. 
- A README markdown file will be maintained to track test results and collective progress

### Branching Strategy
- `main` - Production-ready code
- `feature/*` - Individual feature branches
- `bugfix/*` - Bug fix branches

---

## Helps Links

- [JDK Setup and Installation]()
