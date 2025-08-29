package com.example.case2;

public class AccountItem {
    private Long companyKey;
    private String companyName;
    private Integer isRelated;
    private Long relCompanyKey;
    private Integer allowTransfer;
    private Integer allowQuery;
    private String accountNo;

    public Long getCompanyKey() {
        return companyKey;
    }

    public void setCompanyKey(Long companyKey) {
        this.companyKey = companyKey;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public Integer getIsRelated() {
        return isRelated;
    }

    public void setIsRelated(Integer isRelated) {
        this.isRelated = isRelated;
    }

    public Long getRelCompanyKey() {
        return relCompanyKey;
    }

    public void setRelCompanyKey(Long relCompanyKey) {
        this.relCompanyKey = relCompanyKey;
    }

    public Integer getAllowTransfer() {
        return allowTransfer;
    }

    public void setAllowTransfer(Integer allowTransfer) {
        this.allowTransfer = allowTransfer;
    }

    public Integer getAllowQuery() {
        return allowQuery;
    }

    public void setAllowQuery(Integer allowQuery) {
        this.allowQuery = allowQuery;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

}
