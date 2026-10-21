package com.studyflow.service;

import com.studyflow.model.Student;
import com.studyflow.model.Task;
import com.studyflow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class TaskService {
    private final TaskRepository tasks;

    public TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    public List<Task> getTasks(Student owner) {
        return tasks.findByOwnerIdOrderByDueDateAsc(owner.getId());
    }

    public Task getTask(Student owner, Long id) {
        return tasks.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new IllegalArgumentException("Task not found."));
    }

    @Transactional
    public void create(Student owner, String title, String description, String subject,
                       LocalDate dueDate, String priority) {
        validate(title, subject, priority);
        tasks.save(new Task(title.trim(), cleanDescription(description), subject.trim(),
                dueDate, priority, owner));
    }

    @Transactional
    public void update(Student owner, Long id, String title, String description, String subject,
                       LocalDate dueDate, String priority) {
        validate(title, subject, priority);
        Task task = getTask(owner, id);
        task.update(title.trim(), cleanDescription(description), subject.trim(), dueDate, priority);
    }

    @Transactional
    public void toggleStatus(Student owner, Long id) {
        Task task = getTask(owner, id);
        if ("COMPLETED".equals(task.getStatus())) task.markPending();
        else task.markCompleted();
    }

    @Transactional
    public void delete(Student owner, Long id) {
        tasks.delete(getTask(owner, id));
    }

    private void validate(String title, String subject, String priority) {
        if (title == null || title.trim().isEmpty() || title.trim().length() > 120) {
            throw new IllegalArgumentException("Task title is required (up to 120 characters).");
        }
        if (subject == null || subject.trim().isEmpty() || subject.trim().length() > 80) {
            throw new IllegalArgumentException("Subject is required (up to 80 characters).");
        }
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(priority)) {
            throw new IllegalArgumentException("Choose a valid priority.");
        }
    }

    private String cleanDescription(String description) {
        if (description == null) return "";
        String clean = description.trim();
        return clean.length() > 1000 ? clean.substring(0, 1000) : clean;
    }
}
