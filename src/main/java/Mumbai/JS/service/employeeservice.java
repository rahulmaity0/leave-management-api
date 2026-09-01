package Mumbai.JS.service;
import java.util.List;

import org.springframework.stereotype.Service;

import Mumbai.JS.dto.employeedto;
import Mumbai.JS.exception.EmployeeNotFoundException;
import Mumbai.JS.model.employeemodel;
import Mumbai.JS.repo.employeerepo;

@Service
public class employeeservice {

    // public employeeservice(employeerepo employeerepo){
    //     this.employeerepo=employeerepo;
    // }

    private final employeerepo employeerepo;



    public employeeservice(employeerepo employeerepo){
        this.employeerepo=employeerepo;
    }

    public employeemodel getEmployeee(int id){
       return employeerepo.findById(id).orElseThrow(()->new EmployeeNotFoundException("Yeh id"+ id +"ka employee nahi hai"));//find one
    }

    public List<employeemodel> getAllEmployees(){
        return employeerepo.findAll();//find all
    }

    public void addemployee(employeedto employeedto){
        employeemodel employeemodel=new employeemodel();
        
        employeemodel.setName(employeedto.getName());
        employeemodel.setAddress(employeedto.getAddress());
        employeemodel.setGender(employeedto.getGender());

        //employeemodel.setId(GeneratedId)//something

        employeerepo.save(employeemodel);
    }

    public void deleteemployee(int id){
        employeerepo.deleteById(id);
    }

    
}
