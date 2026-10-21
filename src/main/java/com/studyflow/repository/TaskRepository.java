package com.studyflow.repository;

import com.studyflow.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByOwnerIdOrderByDueDateAsc(Long ownerId);
    Optional<Task> findByIdAndOwnerId(Long id, Long ownerId);
}
