package com.studyflow.service;

import com.studyflow.model.Student;
import com.studyflow.repository.StudentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {
    private final StudentRepository students;
    private final PasswordEncoder passwordEncoder;

    public StudentService(StudentRepository students, PasswordEncoder passwordEncoder) {
        this.students = students;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(String name, String email, String password) {
        String cleanName = name == null ? "" : name.trim();
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();

        if (cleanName.isBlank() || cleanName.length() > 80) {
            throw new IllegalArgumentException("Enter your name (up to 80 characters).");
        }
        if (!cleanEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$") || cleanEmail.length() > 160) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("Password must be between 8 and 72 characters.");
        }
        if (students.existsByEmailIgnoreCase(cleanEmail)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        students.save(new Student(cleanName, cleanEmail, passwordEncoder.encode(password)));
    }
}
