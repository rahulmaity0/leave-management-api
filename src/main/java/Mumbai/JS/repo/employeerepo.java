package Mumbai.JS.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import Mumbai.JS.model.employeemodel;

@Repository
public interface employeerepo extends JpaRepository<employeemodel,Integer> {
    
}
