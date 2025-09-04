package com.example.case2;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** 登入使用者 */
public class LoginUser {
    private static final Logger logger = Logger.getLogger(LoginUser.class.getName());

    private Company company;

    /** 建構子 */
    public Company getCompany() {
        return company;
    }

    /** 取得FX查詢帳號列表 */
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

    /** 取得Level1 */
    public void getLevel1(Company company) {
        List<AccountItem> authzAcntList = getLevel2();
        for (AccountItem accountItem : authzAcntList) {
            if (accountItem.getIsRelated().intValue() == AAConstants.YES) {
                logger.info("==== add 母子公司log accountList的A/C LIST:" + accountItem);
            }
        }
    }

    /**
     * 取得Level2
     * 
     * @return
     */
    private List<AccountItem> getLevel2() {
        int x = 0;
        if (x >= 0) {
            logger.info("==== x >=0, current value:" + x);
        }
        return getLevel3();
    }

    /** 取得Level3 */
    private List<AccountItem> getLevel3() {
        int x = 0;
        if (x >= 0) {
            logger.info("==== x >=0, current value:" + x);
        }
        return new ArrayList<>();
    }

    /** 取得AuthzAcntList */
    private List<AccountItem> getAuthzAcntList() {
        int x = 0;
        if (x >= 0) {
            logger.info("==== x >=0, current value:" + x);
        }
        return new ArrayList<>();
    }

    /** 取得相關公司列表 */
    public List<Company> getRelatedCompanyList() {
        throw new UnsupportedOperationException("Unimplemented method 'getRelatedCompanyList'");
    }
}
