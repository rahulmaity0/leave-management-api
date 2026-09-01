package Mumbai.JS.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;



@Entity
public class employeemodel {

    @ManyToOne
    private  department department;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private int id;
    private String name;
    private String address;
    private char gender;

    // Days of paid leave left this year. Deducted when a leave request is approved,
    // credited back if an approved-but-not-yet-started leave is cancelled.
    private int leaveBalance = 20;

    public employeemodel(){

    }
    public employeemodel(int id,String name,String address,char gender){
        this.id=id;
        this.name=name;
        this.address=address;
        this.gender=gender;

    }

    public int getId(){
        return id;
    }
    public void setId(int id){
        this.id=id;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name=name;
    }

    public void setAddress(String address){
        this.address=address;
    }

    public String getAddress(){
        return address;
    }


    public char getGender(){
        return gender;
    }
    public void setGender(char gender){
        this.gender=gender;
    }

    public int getLeaveBalance(){
        return leaveBalance;
    }

    public void setLeaveBalance(int leaveBalance){
        this.leaveBalance=leaveBalance;
    }

    public department getDepartment() {
    return department;
}

public void setDepartment(department department) {
    this.department = department;
}
}