package com.example.case2;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class LoginUser {
    private static final Logger logger = Logger.getLogger(LoginUser.class.getName());

    private Company company;

    public Company getCompany() {
        return company;
    }

    public List<AccountItem> getFXQueryAcntList(Company company) {
        List<AccountItem> accountItemList = new ArrayList<AccountItem>();
        List<AccountItem> authzAcntList = getAuthzAcntList();

        for (AccountItem accountItem : authzAcntList) {
            accountItem.setCompanyName(company.getCompanyName());
            if (accountItem.getIsRelated().intValue() == AAConstants.YES) {
                if (accountItem.getRelCompanyKey().intValue() == company.getCompanyKey().intValue()) {
                    if (((accountItem.getAllowTransfer().intValue() == AAConstants.YES)
                            || (accountItem.getAllowQuery().intValue() == AAConstants.YES))) {
                        accountItemList.add(accountItem);
                    } else if (accountItem.getAllowQuery().intValue() == AAConstants.YES) {

                        accountItemList.add(accountItem);
                        logger.info("==== add 母子公司log accountList的A/C LIST:" + accountItemList);
                    }

                }
            } else if (accountItem.getCompanyKey().intValue() == company.getCompanyKey().intValue()) {
                if (((accountItem.getAllowTransfer().intValue() == AAConstants.YES)
                        || (accountItem.getAllowQuery().intValue() == AAConstants.YES))) {
                    // if (accountItem.getAllowQuery().intValue() == UAAConstants.YES) {
                    accountItemList.add(accountItem);
                } else if (accountItem.getAllowQuery().intValue() == AAConstants.YES) {

                    accountItemList.add(accountItem);
                    logger.info("==== 母子公司log accountList的A/C LIST:" + accountItemList);
                }

            }

        }
        return accountItemList;
    }

    private List<AccountItem> getAuthzAcntList() {
        throw new UnsupportedOperationException("Unimplemented method 'getAuthzAcntList'");
    }

    public List<Company> getRelatedCompanyList() {
        throw new UnsupportedOperationException("Unimplemented method 'getRelatedCompanyList'");
    }
}
