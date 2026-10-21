package com.studyflow.controller;

import com.studyflow.model.Student;
import com.studyflow.repository.StudentRepository;
import com.studyflow.service.TaskService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
public class TaskController {
    private final TaskService taskService;
    private final StudentRepository students;

    public TaskController(TaskService taskService, StudentRepository students) {
        this.taskService = taskService;
        this.students = students;
    }

    private Student currentStudent(UserDetails user) {
        return students.findByEmailIgnoreCase(user.getUsername())
                .orElseThrow(() -> new IllegalStateException("Logged-in student account not found."));
    }

    @GetMapping("/")
    public String dashboard(@org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails user,
                            @RequestParam(required = false) String filter,
                            Model model) {
        Student student = currentStudent(user);
        var allTasks = taskService.getTasks(student);
        var visibleTasks = allTasks.stream()
                .filter(t -> filter == null || filter.isBlank() || "ALL".equals(filter)
                        || filter.equals(t.getStatus()))
                .toList();

        long completed = allTasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long pending = allTasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count();
        long overdue = allTasks.stream().filter(t -> "PENDING".equals(t.getStatus())
                && t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now())).count();

        model.addAttribute("studentName", student.getName());
        model.addAttribute("tasks", visibleTasks);
        model.addAttribute("filter", filter == null ? "ALL" : filter);
        model.addAttribute("totalCount", allTasks.size());
        model.addAttribute("completedCount", completed);
        model.addAttribute("pendingCount", pending);
        model.addAttribute("overdueCount", overdue);
        return "dashboard";
    }

    @GetMapping("/focus")
    public String focusPage(@org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails user,
                            Model model) {
        model.addAttribute("tasks", taskService.getTasks(currentStudent(user)));
        return "focus";
    }

    @GetMapping("/tasks/new")
    public String newTask() {
        return "task-form";
    }

    @PostMapping("/tasks")
    public String create(@org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails user,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam String subject,
                         @RequestParam(required = false) String dueDate,
                         @RequestParam(defaultValue = "MEDIUM") String priority,
                         Model model) {
        try {
            taskService.create(currentStudent(user), title, description, subject, parseDate(dueDate), priority);
            return "redirect:/";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            addFormValues(model, title, description, subject, dueDate, priority);
            return "task-form";
        }
    }

    @GetMapping("/tasks/{id}/edit")
    public String editPage(@org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails user,
                           @PathVariable Long id, Model model) {
        try {
            var task = taskService.getTask(currentStudent(user), id);
            model.addAttribute("task", task);
            return "task-edit";
        } catch (IllegalArgumentException ex) {
            return "redirect:/?error=notfound";
        }
    }

    @PostMapping("/tasks/{id}/edit")
    public String update(@org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails user,
                         @PathVariable Long id,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam String subject,
                         @RequestParam(required = false) String dueDate,
                         @RequestParam(defaultValue = "MEDIUM") String priority,
                         Model model) {
        try {
            taskService.update(currentStudent(user), id, title, description, subject, parseDate(dueDate), priority);
            return "redirect:/";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("taskId", id);
            addFormValues(model, title, description, subject, dueDate, priority);
            return "task-edit";
        }
    }

    @PostMapping("/tasks/{id}/toggle")
    public String toggle(@org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails user,
                         @PathVariable Long id) {
        try {
            taskService.toggleStatus(currentStudent(user), id);
        } catch (IllegalArgumentException ignored) {
            // If the task does not belong to this student, do not change anything.
        }
        return "redirect:/";
    }

    @PostMapping("/tasks/{id}/delete")
    public String delete(@org.springframework.security.core.annotation.AuthenticationPrincipal UserDetails user,
                         @PathVariable Long id) {
        try {
            taskService.delete(currentStudent(user), id);
        } catch (IllegalArgumentException ignored) {
            // If the task does not belong to this student, do not delete anything.
        }
        return "redirect:/";
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Please enter a valid due date.");
        }
    }

    private void addFormValues(Model model, String title, String description, String subject,
                               String dueDate, String priority) {
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("subject", subject);
        model.addAttribute("dueDate", dueDate);
        model.addAttribute("priority", priority);
    }
}
