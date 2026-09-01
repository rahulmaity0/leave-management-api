package Mumbai.JS.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Mumbai.JS.model.department;
import Mumbai.JS.service.departmentService;

@RestController
@RequestMapping("/departments")
public class departmentController {

    private final departmentService departmentService;

    public departmentController(departmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public List<department> getAllDepartments() {
        return departmentService.getAllDepartments();
    }

    @PostMapping
    public department createDepartment(@RequestBody department department) {
        return departmentService.createDepartment(department);
    }
}