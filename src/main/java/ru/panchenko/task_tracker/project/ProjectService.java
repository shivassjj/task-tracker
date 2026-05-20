package ru.panchenko.task_tracker.project;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.panchenko.task_tracker.project.dto.ProjectResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

   private final ProjectRepository projectRepository;

   public List<ProjectResponse> findAll() {
       return projectRepository.findAll()
               .stream()
               .map(ProjectResponse::from)
               .toList();
   }

   public ProjectResponse findById(Long id) {
       return projectRepository.findById(id)
               .map(ProjectResponse::from)
               .orElseThrow(() -> new EntityNotFoundException("Project not found with id " + id));
   }
}
