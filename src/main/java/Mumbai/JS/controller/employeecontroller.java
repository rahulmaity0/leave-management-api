package Mumbai.JS.controller;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestMapping;

import Mumbai.JS.dto.employeedto;
import Mumbai.JS.model.employeemodel;
import Mumbai.JS.service.employeeservice;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/employees")
public class employeecontroller {
    private final employeeservice employeeservice;


    public employeecontroller(employeeservice employeeservice){
        this.employeeservice=employeeservice;
    }



    @GetMapping("/{id}") //what is requestbody and request mapping etc
    public employeemodel getEmployee(@PathVariable int id){
        return employeeservice.getEmployeee(id);
    }

    @GetMapping
    public List <employeemodel> getall(){
        return employeeservice.getAllEmployees();
    }

    @PostMapping
    public void addemployee(@Valid @RequestBody employeedto employeedto){
        employeeservice.addemployee(employeedto);
        System.out.println("Employee added successfully");
    }
    @DeleteMapping("/{id}")
    public void deleteemployee(@PathVariable int id){
        employeeservice.deleteemployee(id);
        System.out.println("Employee deleted successfully");
    }
}
