package com.nithin.Server.function;

import in.nmaloth.entity.BlockType;
import in.nmaloth.entity.account.AccountAccumValues;
import in.nmaloth.entity.account.AccountBalances;
import in.nmaloth.entity.account.AccountTempBalance;
import in.nmaloth.entity.account.BalanceTypes;
import in.nmaloth.function.model.input.AccountInput;
import in.nmaloth.function.model.output.AccountValidationResponse;
import in.nmaloth.payments.constants.ServiceResponse;
import in.nmaloth.payments.constants.ids.FunctionID;
import in.nmaloth.payments.constants.ids.ServiceID;
import lombok.extern.slf4j.Slf4j;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.*;
import org.apache.geode.distributed.DistributedLockService;
import org.springframework.data.gemfire.function.annotation.Filter;
import org.springframework.data.gemfire.function.annotation.GemfireFunction;
import org.springframework.data.gemfire.function.annotation.RegionData;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class AccountLimitFunction {

    private  Region<String,AccountTempBalance> accountTempBalanceRegion;
    private  Region<String, AccountAccumValues> accountAccumValuesRegion;
    private DistributedLockService lockService;


    public AccountLimitFunction() {


    }


    public void createLockService(GemFireCache gemFireCache){

        this.lockService = DistributedLockService.create("accountLimits",gemFireCache.getDistributedSystem());
    }

    public void checkAccountAccumRegions(GemFireCache gemFireCache){

        if(this.accountAccumValuesRegion == null){
            this.accountAccumValuesRegion = gemFireCache.getRegion("accountAccum");
            if(this.accountAccumValuesRegion == null){
                throw new RuntimeException("Cannot find account Accumulated Region");
            }
        } else {
            log.info(" #################### {}","Region AccountAccum Regions already Created");
        }

        if(accountTempBalanceRegion == null){
            this.accountTempBalanceRegion = gemFireCache.getRegion("accountTempBalance");
            if(this.accountTempBalanceRegion == null){
                throw new RuntimeException("Cannot find account Temp Region");
            }

        } else {
            log.info("#########################{}","Region Account Temp Balance already created");
        }

    }


    @GemfireFunction(id = FunctionID.ACCOUNT_VALIDATOR,optimizeForWrite = true)
    public List<AccountValidationResponse> validateAccountsLimits(@RegionData Map<String, AccountAccumValues> accountAccumValuesMap, @Filter Set<?> keys, AccountInput accountInput){



        List<AccountValidationResponse> validationResponseList = new ArrayList<>();

        keys.forEach(o -> {

            String accountNumber = (String) o;
            validationResponseList.add(validateAccountsLimits(accountAccumValuesMap.get(accountNumber),accountInput,accountNumber));

        });

        return validationResponseList;

    }

    private AccountValidationResponse validateAccountsLimits(AccountAccumValues accountAccumValues, AccountInput accountInput, String accountNumber) {

        AccountValidationResponse.AccountValidationResponseBuilder builder = AccountValidationResponse.builder()
                .idNumber(accountNumber)
                .messageId(accountInput.getMessageId())
                .serviceId(ServiceID.ACCOUNT_VALIDATOR);

        if(accountAccumValues == null){
            return getAccountValidationResponse(builder, ServiceResponse.NO_ENTRY,0,0);
        }

        if(accountAccumValues.getBlockType().equals(BlockType.APPROVE) || (accountAccumValues.getBlockType().equals(BlockType.VIP_ALWAYS_APPROVE))){
        } else {
            return getAccountValidationResponse(builder, evaluateBlock(accountAccumValues.getBlockType()),0,0);
        }


        ServiceResponse serviceResponse = null;

        Map<BalanceTypes,Long> balanceTypesTempMap;
        balanceTypesTempMap = new HashMap<>();

        try {

            lockService.lock(accountNumber,-1,-1);

        } catch (Exception ex){
            ex.printStackTrace();
            serviceResponse = ServiceResponse.SYSTEM_ERROR;
            return getAccountValidationResponse(builder,serviceResponse,0,0);
        }


        log.info("Entered Here........1");
        try {
            List<AccountTempBalance> accountTempBalanceList = getAllAccountTempBalance(accountNumber);

            if(accountTempBalanceList == null){
                accountTempBalanceRegion.put(accountInput.getMessageId(),createAccountTempBalance(accountInput,accountNumber));
            } else {
                balanceTypesTempMap  =  findAccumulatedAmount(accountTempBalanceList);
                accountTempBalanceRegion.put(accountInput.getMessageId(),createAccountTempBalance(accountInput,accountNumber));
            }

        } catch (NameResolutionException | TypeMismatchException | QueryInvocationTargetException | FunctionDomainException e) {
            e.printStackTrace();
            serviceResponse = ServiceResponse.SYSTEM_ERROR;
        } catch (Exception ex){
            ex.printStackTrace();
            serviceResponse = ServiceResponse.SYSTEM_ERROR;
        } finally {
            lockService.unlock(accountNumber);
            if(serviceResponse != null){
                return getAccountValidationResponse(builder,serviceResponse,0,0);
            }
        }

        log.info("Entered Here........2");

        if(accountInput.getTransactionAmount() > 0){
            for(BalanceTypes balanceTypes: accountInput.getBalanceTypesList()){
                if(evaluateLimit(accountAccumValues.getLimitsMap().get(balanceTypes), accountAccumValues.getBalancesMap().get(balanceTypes),
                        accountInput.isDebit(),accountInput.getTransactionAmount(),balanceTypesTempMap.get(balanceTypes))){
                    serviceResponse = ServiceResponse.ACCT_LIMIT;
                    break;
                }
            }
        }

        if(serviceResponse == null){

            Long accumulatedBalance = balanceTypesTempMap.get(BalanceTypes.CURRENT_BALANCE);
            if(accumulatedBalance == null){
                accumulatedBalance = 0L;
            }
            AccountBalances accountBalance = accountAccumValues.getBalancesMap().get(BalanceTypes.CURRENT_BALANCE);
            Long limit = accountAccumValues.getLimitsMap().get(BalanceTypes.CURRENT_BALANCE);
            if(accountInput.isDebit()){
                long balance = accountBalance.getPostedBalance() + accountBalance.getMemoDb()
                        - accountBalance.getMemoCr() + accumulatedBalance.longValue() + accountInput.getTransactionAmount();
                long otb = limit - balance;
                return getAccountValidationResponse(builder, ServiceResponse.OK,otb,balance);

            } else {
                long balance  = accountBalance.getPostedBalance() + accountBalance.getMemoDb()
                        - accountBalance.getMemoCr() + accumulatedBalance - accountInput.getTransactionAmount();
                long otb = limit - balance;
                return getAccountValidationResponse(builder, ServiceResponse.OK,otb,balance);
            }

        } else {
            return getAccountValidationResponse(builder,serviceResponse,0,0);
        }
    }

    private AccountTempBalance createAccountTempBalance(AccountInput accountInput, String accountNumber) {

        long transactionAmount = 0L;

        if(accountInput.isDebit()){
            transactionAmount = accountInput.getTransactionAmount();
        } else {
            transactionAmount = accountInput.getTransactionAmount()* -1;
        }

        return AccountTempBalance.builder()
                .accountNumber(accountNumber)
                .amount(transactionAmount)
                .balanceTypesList(accountInput.getBalanceTypesList())
                .id(accountInput.getMessageId())
                .build();

    }


    private boolean evaluateLimit(Long limit, AccountBalances accountBalances, boolean debit, long transactionAmount , Long tempBalance) {
        long balance;

        if(accountBalances == null){
            return false;
        }

        if(debit){
            balance = accountBalances.getPostedBalance() + accountBalances.getMemoDb() - accountBalances.getMemoCr()  + transactionAmount ;
        } else {
            balance = accountBalances.getPostedBalance() + accountBalances.getMemoDb() - accountBalances.getMemoCr() - transactionAmount;
        }

        if(tempBalance != null){
            balance = balance + tempBalance.longValue();
        }

        if(balance > limit){
            return true;
        }

        return false;
    }

    private Map<BalanceTypes,Long> findAccumulatedAmount(List<AccountTempBalance> accountTempBalanceList) {

        Map<BalanceTypes,Long> balanceTypesMap = new HashMap<>();

        for ( AccountTempBalance accountTempBalance: accountTempBalanceList) {

            updateAccumulatedTempAmount(balanceTypesMap,accountTempBalance.getBalanceTypesList(),accountTempBalance.getAmount());
        }
        return balanceTypesMap;
    }

    private void updateAccumulatedTempAmount(Map<BalanceTypes, Long> balanceTypesMap, List<BalanceTypes> balanceTypesList,
                                             long txnAmount) {

        for (BalanceTypes balanceTypes:balanceTypesList) {
            Long amount = balanceTypesMap.get(balanceTypes);

            if(amount == null){
                balanceTypesMap.put(balanceTypes,txnAmount);
            } else {
                balanceTypesMap.put(balanceTypes,txnAmount + amount);
            }
        }
    }


    private AccountValidationResponse getAccountValidationResponse(AccountValidationResponse.AccountValidationResponseBuilder builder,
                                                                   ServiceResponse serviceResponse, long otb, long balance) {
        return builder
                .serviceResponse(serviceResponse)
                .accountBalance(balance)
                .otb(otb)
                .build();
    }

    private ServiceResponse evaluateBlock(BlockType blockType){
        switch (blockType){
            case BLOCK_TEMP:{
                return ServiceResponse.TEMP_BLK;
            }
            case BLOCK_SUSPECTED_FRAUD:{
                return ServiceResponse.SUSPECT_FRAUD;
            }
            case BLOCK_FRAUD:{
                return ServiceResponse.FRAUD;
            }
            case BLOCK_PICKUP:{
                return ServiceResponse.PICK_UP;
            }
            default:{
                return ServiceResponse.BLK;
            }
        }

    }

    private List<AccountTempBalance> getAllAccountTempBalance(String accountNumber) throws NameResolutionException, TypeMismatchException, QueryInvocationTargetException, FunctionDomainException {
        String query = "select * from /accountTempBalance  accountTempBalance where accountTempBalance.accountNumber = '&'".replace("&", accountNumber);

        SelectResults<AccountTempBalance> selectResults = accountTempBalanceRegion.query(query);
        return selectResults.asList();

    }

}
