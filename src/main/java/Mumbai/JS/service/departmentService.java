package Mumbai.JS.service;

import java.util.List;

import org.springframework.stereotype.Service;

import Mumbai.JS.model.department;
import Mumbai.JS.repo.departmentRepository;

@Service
public class departmentService {

    private final departmentRepository departmentRepository;

    public departmentService(departmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    public List<department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public department getDepartmentById(int id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
    }

    public department createDepartment(department department) {
        return departmentRepository.save(department);
    }
}