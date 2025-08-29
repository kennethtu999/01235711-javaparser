package com.example.case2;

public class Company {

    private String companyName;
    private Long companyKey;

    public String getOuid() {
        throw new UnsupportedOperationException("Unimplemented method 'getOuid'");
    }

    public String getCompanyName() {
        return companyName;
    }

    public Long getCompanyKey() {
        return companyKey;
    }

    public void setCompanyKey(Long companyKey) {
        this.companyKey = companyKey;
    }

}
