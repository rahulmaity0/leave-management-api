package Mumbai.JS.dto;

import jakarta.validation.constraints.NotBlank;

public class employeedto {

    @NotBlank
    private String name;

    @NotBlank
    private String address;
    private char gender;
    
    public employeedto(){

    }
    public employeedto(String name,String address,char gender){
        this.name=name;
        this.address=address;
        this.gender=gender; 
}

    public String getName(){
        return name;
    }
    public void setName(String name){
        this.name=name;
    }

    public String getAddress(){
        return address;
    }
    public void setAddress(String address){
        this.address=address;
    }

    public char getGender(){
        return gender;
    }
    public void setGender(char gender){
        this.gender=gender;
    }

}