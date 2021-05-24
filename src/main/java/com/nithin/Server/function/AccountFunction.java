package com.nithin.Server.function;


import in.nmaloth.entity.BlockType;
import in.nmaloth.entity.account.AccountBasic;
import in.nmaloth.function.model.input.AccountInput;
import in.nmaloth.function.model.output.ValidationResponse;
import in.nmaloth.payments.constants.ServiceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.gemfire.function.annotation.RegionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

//@Component
@Slf4j
public class AccountFunction {

//    @GemfireFunction(id = "validateAccounts")
    public List<ValidationResponse> validateAccounts(@RegionData Map<String, AccountBasic> accountBasicMap, Set<?> keys, AccountInput accountInput){

        List<ValidationResponse> validationResponseList = new ArrayList<>();

        keys.forEach(o -> {

            String accountNumber = (String) o;
            validationResponseList.add(validateAccounts(accountBasicMap.get(accountNumber),accountInput,accountNumber));

        });

        return validationResponseList;

    }

    private ValidationResponse validateAccounts(AccountBasic accountBasic, AccountInput accountInput, String accountNumber) {

        ValidationResponse.ValidationResponseBuilder builder = ValidationResponse.builder()
                .idNumber(accountNumber)
                .messageId(accountInput.getMessageId())
                .serviceId("accountValidator");

        if(accountBasic == null){
            return getAccountValidationResponse(builder, ServiceResponse.NO_ENTRY);
        }

        if(accountBasic.getBlockType().equals(BlockType.APPROVE) || (accountBasic.getBlockType().equals(BlockType.VIP_ALWAYS_APPROVE))){
        } else {
            return getAccountValidationResponse(builder, evaluateBlock(accountBasic.getBlockType()));
        }

        return getAccountValidationResponse(builder, ServiceResponse.OK);

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

    private ValidationResponse getAccountValidationResponse(ValidationResponse.ValidationResponseBuilder builder,
                                                            ServiceResponse serviceResponse) {
        return builder
                .serviceResponse(serviceResponse)
                .build();
    }
}
