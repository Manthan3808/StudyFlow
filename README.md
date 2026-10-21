# StudyFlow — Student Task Manager

A beginner-friendly Java mini project built with Spring Boot, Thymeleaf, Spring Security, Spring Data JPA and an H2 file database.

## Features
- Student registration and login
- BCrypt password hashing through Spring Security
- Student-specific tasks
- Create, edit, delete and complete tasks
- Subject, due date and priority fields
- Dashboard totals for pending, completed and overdue tasks
- Filter tasks by status
- Focus Mode with 25-minute focus, 5-minute short break, and 15-minute long break timer
- Completed focus-session counter and optional task selection
- Responsive CSS interface
- CSRF protection for form submissions through Spring Security

## Requirements
- JDK 17 or newer
- Internet connection on first build (Maven downloads dependencies)
- IntelliJ IDEA, Eclipse, Spring Tool Suite or VS Code with Java support

## Run the project
1. Install JDK 17 or newer.
2. Extract the ZIP.
3. Open the `student-task-manager` folder in your IDE as a Maven project.
4. Wait for Maven to download dependencies.
5. Run `src/main/java/com/studyflow/StudentTaskManagerApplication.java`.
6. Open http://localhost:8080 in your browser.
7. Select **Create an account**, register with your own email and a password of at least 8 characters, then log in.

Alternatively, from a terminal inside the project folder:
- Windows: `mvnw.cmd spring-boot:run` (if the Maven wrapper is present in your generated project), or `mvn spring-boot:run` if Maven is installed.
- macOS/Linux: `./mvnw spring-boot:run` (if the Maven wrapper is present), or `mvn spring-boot:run`.

This starter ZIP does not include Maven wrapper scripts, so use an IDE with Maven support or install Maven to run the command.

## Database
H2 is configured in file mode at `./data/studyflowdb`. The application creates/updates tables automatically for this mini-project. The H2 console is intentionally not enabled.

## Security notes
- Passwords are hashed with BCrypt, not stored as plain text.
- Unauthenticated requests are redirected to login.
- Each task query is scoped to the currently logged-in student.
- Spring Security's CSRF protection remains enabled for POST forms.
- This is an educational local project, not a production deployment. For a public deployment, add HTTPS, email verification, password reset, rate limiting, stronger operational configuration, and a reviewed production database configuration.

## Main packages
- `model`: JPA entities
- `repository`: database access
- `service`: application rules
- `controller`: web routes
- `config`: security configuration
- `templates`: Thymeleaf pages
- `static/css`: styling

## Suggested manual test plan
1. Register a new student.
2. Log out and log back in.
3. Add a Java task with a due date and high priority.
4. Add a second task for another subject.
5. Mark a task complete and confirm dashboard counts change.
6. Open Focus mode, switch between focus and break timers, pause, resume, reset, and skip.
6. Filter by pending and completed.
7. Edit a task and check the changes.
8. Delete a task.
9. Register a second student and confirm they cannot see the first student's tasks.
10. Try accessing `/` while logged out; the app should ask you to log in.
11. Restart the app and verify the file database retains the tasks.

## Mini-project report outline
1. Title page
2. Certificate / acknowledgement (if your college requires them)
3. Abstract
4. Introduction
5. Problem statement
6. Objectives
7. Hardware and software requirements
8. System architecture
9. Module description
10. Database design
11. Implementation screenshots
12. Test cases and results
13. Limitations and future scope
14. Conclusion
15. References
