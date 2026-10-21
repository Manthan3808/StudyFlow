# StudyFlow — Student Task Manager Using Java

## 1. Abstract
StudyFlow is a web-based Student Task Manager developed using Java and Spring Boot. It helps students organise assignments, practical work, and other academic responsibilities. Students can create an account, log in, create and manage tasks, assign a subject and priority, set a due date, and track whether work is pending or completed. The application uses Spring Security for authentication, BCrypt password hashing, Thymeleaf for server-rendered web pages, Spring Data JPA for persistence, and an H2 file database for storing information.

## 2. Introduction
Students often manage academic work using notebooks, messages, and separate reminders. A central task manager provides one place to record deadlines and track progress. StudyFlow is designed as a simple and accessible tool for day-to-day academic planning.

## 3. Problem Statement
Manual task tracking can cause missed deadlines, duplicated notes, and difficulty identifying incomplete work. The project addresses this problem by providing a digital system for organising academic tasks and monitoring completion.

## 4. Objectives
- Develop a user-friendly student task management website.
- Provide student registration, login, and logout.
- Allow users to create, view, update, delete, and complete tasks.
- Store task title, description, subject, due date, priority, and status.
- Display counts for total, pending, completed, and overdue tasks.
- Provide a Pomodoro focus timer with break modes to encourage structured study sessions.
- Keep each student's task list separate from other students.

## 5. Scope
The application is suitable for a local academic mini-project demonstration. It can manage assignments, practical submissions, revision activities, and other personal study tasks. Future versions could add notifications, calendar views, collaborative tasks, and cloud deployment.

## 6. Software and Hardware Requirements
### Software
- Java Development Kit (JDK) 17 or later
- Spring Boot 3.5.x
- Maven
- Thymeleaf, HTML, CSS
- Spring Security
- Spring Data JPA
- H2 database
- Modern web browser
- IntelliJ IDEA, Eclipse, Spring Tool Suite, or VS Code

### Hardware
- A computer capable of running a JDK and a modern browser
- Internet connection for downloading Maven dependencies on first setup

## 7. System Architecture
The browser sends requests to Spring MVC controllers. Controllers call service classes, which enforce application rules. Repositories use Spring Data JPA to store and retrieve entities in the H2 database. Spring Security protects application routes and authenticates users. Thymeleaf renders HTML pages from server-side data.

## 8. Modules
1. **Registration module:** validates name, email, and password; prevents duplicate email registration.
2. **Authentication module:** verifies credentials and manages the logged-in session.
3. **Task module:** creates, updates, deletes, and changes task status.
4. **Dashboard module:** displays task totals and supports status filtering.
5. **Persistence module:** stores student and task data in the database.
6. **Focus and Pomodoro module:** provides 25-minute focus sessions, 5-minute short breaks, 15-minute long breaks, pause/resume/reset controls, and a completed-session counter.

## 9. Database Design
### Student table
- `id`: primary key
- `name`: student's name
- `email`: unique email address
- `password_hash`: BCrypt password hash

### Student task table
- `id`: primary key
- `title`: task title
- `description`: task details
- `subject`: academic subject
- `due_date`: optional deadline
- `priority`: LOW, MEDIUM, or HIGH
- `status`: PENDING or COMPLETED
- `student_id`: foreign key referencing the owner

Relationship: one student can own many tasks; each task belongs to one student.

## 10. Security
Spring Security protects authenticated routes. Passwords are stored as BCrypt hashes, not plain text. Task access is filtered by the current student's ID so a user cannot update or delete another student's task by changing a URL. CSRF protection remains enabled for POST forms. The project is intended for local educational use; production deployment would require additional review and controls.

## 11. Testing
Execute the following test cases and record the actual results after running the application.

| Test case | Expected result |
|---|---|
| Register with valid details | Account is created |
| Register with an existing email | Helpful validation error |
| Register with mismatched passwords | Error is displayed |
| Log in with valid credentials | Dashboard opens |
| Log in with invalid credentials | Login error is displayed |
| Add a task | Task appears on dashboard |
| Edit a task | Updated details are displayed |
| Mark a task complete | Status and dashboard count update |
| Delete a task | Task is removed |
| Filter pending/completed | Only matching tasks appear |
| Focus timer start/pause/reset | Timer controls respond correctly |
| Focus timer reaches zero | Session finishes and next break/focus mode is selected |
| Open dashboard while logged out | Redirected to login |
| Log in as a different student | First student's tasks are not visible |
| Restart the application | Saved data remains in file database |

## 12. Limitations
The current version does not send email reminders, verify email ownership, provide password reset, or include an integrated calendar. Focus-session counts are kept in the browser for the current page session and are not yet stored in the database. The H2 database is intended for local demonstration and small-scale use.

## 13. Future Scope
- MySQL database support
- Email or in-app deadline reminders
- Calendar view and weekly schedule
- Search and subject-specific reports
- Study timer and productivity statistics
- Deployment to a secure hosting environment

## 14. Conclusion
StudyFlow demonstrates how Java, Spring Boot, Spring Security, Thymeleaf, and database persistence can be combined to create a useful student-focused web application. It applies object-oriented programming, MVC architecture, CRUD operations, input validation, authentication, and relational data modelling in a practical mini project.

## 15. References
- Spring Boot documentation: https://docs.spring.io/spring-boot/
- Spring Security documentation: https://docs.spring.io/spring-security/reference/
- Spring Initializr: https://start.spring.io/
- Spring Data JPA documentation: https://spring.io/guides/gs/accessing-data-jpa/
