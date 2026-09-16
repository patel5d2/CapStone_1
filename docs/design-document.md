# CampusBridge — Original Design Document (Fall 2026)

> Archived from `README.md` on 2026-09-16, when the README was rewritten for the
> CampusBridge marketplace. Preserved because the use cases, user stories and class
> diagram are Fall 2026 course deliverables (Timeline Tasks 3 and 5).
>
> **These requirements describe the student directory only** — they predate the
> marketplace pivot. The current, complete set of graded requirements is the 11 success
> criteria in [`context/1_overview.md`](../context/1_overview.md). Refreshing the
> storyboard and class diagram for the four-tab app is Sprint 0 work (S0-4, S0-5).
>
> The class-diagram description below is retained verbatim and contains a known error:
> it describes `Task`, `Project`, `TaskRepository`, `TaskService`, `TaskController` and
> `TaskDTO` entities that have never existed in this codebase. Corrected in S0-5.

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

![UML Diagram](../classUMLDiagram.png)

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
