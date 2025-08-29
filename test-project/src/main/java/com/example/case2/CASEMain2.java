package com.example.case2;

import java.util.List;
import java.util.logging.Logger;

public class CASEMain2 {
    private static final Logger logger = Logger.getLogger(CASEMain2.class.getName());

    private LoginUser loginUser = new LoginUser();

    public void initViewForm(CASE001_1_Param viewParam, CASE001_1_View viewForm)
            throws ActionException {
        try {
            logger.info("-------------init CACQ001----------------");

            List<AccountItem> allQueriableAcctList = getLoginUser().getFXQueryAcntList(getLoginUser().getCompany());

            List<Company> relateCompanyList = getLoginUser().getRelatedCompanyList();
            int i = allQueriableAcctList.size();
            for (Company company : relateCompanyList) {

                List<AccountItem> accountList = getLoginUser().getFXQueryAcntList(company);
                logger.info("==== accountList的A/C LIST:" +
                        accountList.size());
                int accountCount = accountList != null ? accountList.size() : 0;
                i = i + accountCount;
            }

            if (i == 0) {
                logger.info("--------TW FR 都無可供查詢帳號------------");
                throwActionException(ICASECode.b2C_NO_QUERY_FROM_ACCOUNT);
            } else {
                logger.info("--------TW FR 的可查詢帳號------------");
                viewForm.setQueryTime(CASEUtils.getDisplayDateTime(new java.util.Date())); //

                viewForm.setComboCompany(buildCompanyCombo(getLoginUser(),
                        getLoginUser().getCompany()));
                viewForm.setCoutOuId(getLoginUser().getCompany().getOuid());
                buildBalanceQueryGrid();
            }
            // 儲存成功的 AccessLog
            saveSuccessAccessLog(AccessLog.CODE_QUERY, "");
        } catch (Exception e) {
            // 儲存失敗的 AccessLog
            ActionException ae = throwActionException(e);
            saveFailedAccessLog(AccessLog.CODE_QUERY, "",
                    ae.getStatus().getStatusCode());
            throw ae;
        }
    }

    public void initViewForm2(CASE001_1_Param viewParam, CASE001_1_View viewForm) throws ActionException {
        List<Company> relateCompanyList = getLoginUser().getRelatedCompanyList();
        for (Company company : relateCompanyList) {
            List<AccountItem> accountList = getLoginUser().getFXQueryAcntList(company);
            logger.info("==== add log accountList的A/C LIST:" + accountList.size());
        }
    }

    private void saveSuccessAccessLog(String codeQuery, String string) {
        throw new UnsupportedOperationException("Unimplemented method 'saveSuccessAccessLog'");
    }

    private void saveFailedAccessLog(String codeQuery, String string, String statusCode) {
        throw new UnsupportedOperationException("Unimplemented method 'saveFailedAccessLog'");
    }

    private void buildBalanceQueryGrid() {
        throw new UnsupportedOperationException("Unimplemented method 'buildBalanceQueryGrid'");
    }

    private Object buildCompanyCombo(LoginUser loginUser2, Object company) {
        throw new UnsupportedOperationException("Unimplemented method 'buildCompanyCombo'");
    }

    private ActionException throwActionException(Object b2c_NO_QUERY_FROM_ACCOUNT) {
        throw new UnsupportedOperationException("Unimplemented method 'throwActionException'");
    }

    private LoginUser getLoginUser() {
        return new LoginUser();
    }
}