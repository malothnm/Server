package com.nithin.Server.function;

import in.nmaloth.entity.BlockType;
import in.nmaloth.entity.card.CardStatus;
import in.nmaloth.entity.card.CardsBasic;
import in.nmaloth.entity.card.Plastic;
import in.nmaloth.function.model.input.CardsInput;
import in.nmaloth.function.model.output.ValidationResponse;
import in.nmaloth.payments.constants.CashBack;
import in.nmaloth.payments.constants.EntryMode;
import in.nmaloth.payments.constants.InstallmentType;
import in.nmaloth.payments.constants.ServiceResponse;
import in.nmaloth.payments.constants.ids.FunctionID;
import in.nmaloth.payments.constants.ids.ServiceID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.gemfire.function.annotation.Filter;
import org.springframework.data.gemfire.function.annotation.FunctionId;
import org.springframework.data.gemfire.function.annotation.GemfireFunction;
import org.springframework.data.gemfire.function.annotation.RegionData;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.*;

@Component
@Slf4j
public class CardsFunction {


    @GemfireFunction(id = FunctionID.CARDS_VALIDATOR)
    public List<ValidationResponse> cardsValidator(@RegionData Map<String, CardsBasic> cardsBasicMap, @Filter Set<?> keys, CardsInput cardsInput){

        List<ValidationResponse> validationResponseList = new ArrayList<>();

        keys.forEach(o -> {
            String key = (String) o;
            ValidationResponse validationResponse = validateCards(cardsBasicMap.get(key),cardsInput,key);
            validationResponseList.add(validationResponse);
        });

        return validationResponseList;

    }

    private ValidationResponse validateCards(CardsBasic cardsBasic,
                                             CardsInput cardsInput, String cardNumber) {

        ValidationResponse.ValidationResponseBuilder builder = ValidationResponse.builder()
                .idNumber(cardNumber)
                .messageId(cardsInput.getMessageId())
                .serviceId(ServiceID.CARD_VALIDATOR);


        if(cardsBasic == null){
            return getCardsValidationResponse(builder, ServiceResponse.NO_ENTRY);
        }

        if(cardsBasic.getCardStatus().equals(CardStatus.ACTIVE) ||(cardsBasic.getCardStatus().equals(CardStatus.INACTIVE))){
        } else {
            return getCardsValidationResponse(builder, ServiceResponse.STATUS);
        }

        if(cardsBasic.getBlockType().equals(BlockType.APPROVE) || (cardsBasic.getBlockType().equals(BlockType.VIP_ALWAYS_APPROVE))){
        } else {
            return getCardsValidationResponse(builder, evaluateBlock(cardsBasic.getBlockType()));
        }

        Boolean block;
        if(cardsBasic.getBlockTransactionType() != null){
            block =  cardsBasic.getBlockTransactionType().get(cardsInput.getTransactionType());
            if(block != null && block){
                return getCardsValidationResponse(builder, ServiceResponse.TRANSACTION_TYPE);
            }
        }

        if(cardsBasic.getBlockTerminalType() != null){
            block = cardsBasic.getBlockTerminalType().get(cardsInput.getTerminalType());
            if(block != null && block){
                return getCardsValidationResponse(builder, ServiceResponse.TERMINAL_TYPE);
            }
        }

        if(cardsBasic.getBlockPurchaseTypes() != null){
            block = cardsBasic.getBlockPurchaseTypes().get(cardsInput.getPurchaseTypes());
            if(block != null && block){
                return getCardsValidationResponse(builder, ServiceResponse.PURCHASE_TYPE);
            }
        }

        if(cardsBasic.getBlockEntryMode() != null){
            block = cardsBasic.getBlockEntryMode().get(cardsInput.getEntryMode());
            if (block != null && block){
                return getCardsValidationResponse(builder,ServiceResponse.ENTRY_MODE);
            }
        }


        if(cardsBasic.getBlockCashBack() != null && cardsBasic.getBlockCashBack() && cardsInput.getCashBack().equals(CashBack.CASH_BACK_PRESENT)){
            return getCardsValidationResponse(builder, ServiceResponse.CASH_BACK);
        }

        if(cardsBasic.getBlockInstallments() != null && cardsBasic.getBlockInstallments() && cardsInput.getInstallmentType().equals(InstallmentType.INSTALLMENT_TYPE)){
            return getCardsValidationResponse(builder, ServiceResponse.INSTALLMENT);
        }

        if(cardsInput.getExpiryDate().isBefore(LocalDate.now())){
            return getCardsValidationResponse(builder,ServiceResponse.EXPIRED_CARD);
        }


        Optional<Plastic> plasticOptional = findPlastic(cardsInput.getExpiryDate(),cardsBasic.getPlasticList());

        if(plasticOptional.isPresent()){
            ServiceResponse serviceResponse = validatePlastic(plasticOptional.get(),cardsInput,cardsBasic);
            return getCardsValidationResponse(builder, serviceResponse);
        } else {
            if(isPlasticPresent(cardsInput.getEntryMode())){
                return getCardsValidationResponse(builder, ServiceResponse.PLASTIC_INVALID);
            }
        }

        return getCardsValidationResponse(builder, ServiceResponse.OK);
    }

    private ValidationResponse getCardsValidationResponse(ValidationResponse.ValidationResponseBuilder builder,
                                                          ServiceResponse serviceResponse) {
        return builder
                .serviceResponse(serviceResponse)
                .build();
    }

    private boolean isPlasticPresent(EntryMode entryMode) {

        switch (entryMode){
            case ICC:
            case MAG:
            case FALLBACK:
            case CONTACT_LESS_ICC:
            case CONTACT_LESS_MAG:
            case MAG_CVV_NOT_POSSIBLE:
            case ICC_INVALID_CVV_I_CVV:{
                return true;
            }
            default:{
                return false;
            }
        }
    }

    private ServiceResponse validatePlastic(Plastic plastic, CardsInput cardsInput, CardsBasic cardsBasic) {

        if(!plastic.getCardActivated()){
            if(cardsBasic.getWaiverDaysActivation() != null && cardsBasic.getWaiverDaysActivation() > 0){
                if(plastic.getDatePlasticIssued() == null){
                    return ServiceResponse.PLASTIC_ACT;
                }

                if(plastic.getDatePlasticIssued().plusDays(cardsBasic.getWaiverDaysActivation())
                        .isBefore(ChronoLocalDateTime.from(LocalDateTime.now()))){
                    return ServiceResponse.PLASTIC_ACT;
                }
            }
        }

        return ServiceResponse.OK;
    }

    private Optional<Plastic> findPlastic(LocalDate expiryDate, List<Plastic> plasticList) {

        if(plasticList != null){
            for (int i = 0; i < plasticList.size(); i ++ ){
                if(plasticList.get(i).getExpiryDate().isEqual(expiryDate)){
                    return Optional.of(plasticList.get(i));
                }

            }
        }

        return Optional.empty();

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

}
