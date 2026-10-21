package com.studyflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "student_tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 80)
    private String subject;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, length = 12)
    private String priority = "MEDIUM";

    @Column(nullable = false, length = 15)
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student owner;

    protected Task() {}

    public Task(String title, String description, String subject, LocalDate dueDate,
                String priority, Student owner) {
        this.title = title;
        this.description = description;
        this.subject = subject;
        this.dueDate = dueDate;
        this.priority = priority;
        this.owner = owner;
        this.status = "PENDING";
    }

    public void update(String title, String description, String subject, LocalDate dueDate, String priority) {
        this.title = title;
        this.description = description;
        this.subject = subject;
        this.dueDate = dueDate;
        this.priority = priority;
    }

    public void markCompleted() { this.status = "COMPLETED"; }
    public void markPending() { this.status = "PENDING"; }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSubject() { return subject; }
    public LocalDate getDueDate() { return dueDate; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public Student getOwner() { return owner; }
}
