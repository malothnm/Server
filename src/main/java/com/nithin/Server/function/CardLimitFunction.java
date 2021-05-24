package com.nithin.Server.function;

import in.nmaloth.entity.account.AccountBalances;
import in.nmaloth.entity.card.*;
import in.nmaloth.entity.product.ProductId;
import in.nmaloth.entity.product.ProductLimitsDef;
import in.nmaloth.function.model.input.CardsLimitInput;
import in.nmaloth.function.model.output.ValidationResponse;
import in.nmaloth.function.model.temp.CacheCardTempAccum;
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

import java.time.LocalDateTime;
import java.util.*;


@Slf4j
@Component
public class CardLimitFunction {

    private Region<ProductId, ProductLimitsDef> productLimitsDefRegion;
    private Region<String, CardTempBalance> cardTempBalanceRegion;
    private Region<String, CardAccumulatedValues> cardAccumulatedValuesRegion;
    private DistributedLockService lockService;


    public CardLimitFunction() {

    }

    public void updateRegionInfo(GemFireCache gemFireCache) {

        this.cardAccumulatedValuesRegion = gemFireCache.getRegion("cardAccum");
        if (this.cardAccumulatedValuesRegion == null) {
            throw new RuntimeException("Cannot create region Card Accumulated ");
        }

        this.productLimitsDefRegion = gemFireCache.getRegion("productLimit");
        if (this.productLimitsDefRegion == null) {
            throw new RuntimeException("Cannot create region ProductLimit ");
        }

        this.cardTempBalanceRegion = gemFireCache.getRegion("cardTempBalance");
        if (this.cardTempBalanceRegion == null) {
            throw new RuntimeException("Cannot create region Card Temp Balance ");
        }

    }

    public void createLockService(GemFireCache gemFireCache) {

        this.lockService = DistributedLockService.create("cardsBalance", gemFireCache.getDistributedSystem());

    }

    @GemfireFunction(id = FunctionID.CARD_LIMIT_VALIDATOR, optimizeForWrite = true)
    public List<ValidationResponse> validateCardLimits(@RegionData Map<String, CardAccumulatedValues> cardAccumulatedValuesMap, @Filter Set<?> keys, CardsLimitInput cardsLimitInput) {

        List<ValidationResponse> validationResponseList = new ArrayList<>();

        keys.forEach(o -> {

            String cardNumber = (String) o;
            validationResponseList.add(validateCardLimits(cardAccumulatedValuesMap.get(cardNumber), cardsLimitInput, cardNumber));

        });

        return validationResponseList;


    }

    private ValidationResponse validateCardLimits(CardAccumulatedValues cardAccumulatedValues, CardsLimitInput cardsLimitInput, String cardNumber) {

        ValidationResponse.ValidationResponseBuilder builder = ValidationResponse.builder()
                .idNumber(cardNumber)
                .messageId(cardsLimitInput.getMessageId())
                .serviceId(ServiceID.CARD_LIMIT_VALIDATOR);


        if (cardAccumulatedValues == null) {
            return getValidationResponse(builder, ServiceResponse.NO_ENTRY);
        }

        Map<LimitType, CacheCardTempAccum> cacheCardTempAccumMap = null;
        ServiceResponse serviceResponse = null;
//        try {
//            long lockTimeStart = System.nanoTime();
//            lockService.lock(cardNumber, -1, -1);
//            long lockEndTime = System.nanoTime();
//            log.info(" Total time for the lock {}", (lockEndTime - lockTimeStart));
//        } catch (Exception ex) {
//            ex.printStackTrace();
//            return getValidationResponse(builder, ServiceResponse.SYSTEM_ERROR);
//        }

        try {

            List<CardTempBalance> cardTempBalanceList = getAllCardTempBalance(cardNumber);

            if (cardTempBalanceList == null) {

                cardTempBalanceRegion.put(cardsLimitInput.getMessageId(), createNewCardTempBalance(cardNumber, cardsLimitInput));
            } else {
                cacheCardTempAccumMap = findAccumulatedAmount(cardTempBalanceList);
                cardTempBalanceRegion.put(cardsLimitInput.getMessageId(), createNewCardTempBalance(cardNumber, cardsLimitInput));
            }

        } catch (FunctionDomainException e) {
            e.printStackTrace();
            serviceResponse = ServiceResponse.SYSTEM_ERROR;
        } catch (QueryInvocationTargetException e) {
            e.printStackTrace();
            serviceResponse = ServiceResponse.SYSTEM_ERROR;
        } catch (TypeMismatchException e) {
            e.printStackTrace();
            serviceResponse = ServiceResponse.SYSTEM_ERROR;
        } catch (NameResolutionException e) {
            e.printStackTrace();
            serviceResponse = ServiceResponse.SYSTEM_ERROR;
        } catch (Exception ex) {
            ex.printStackTrace();
            serviceResponse = ServiceResponse.SYSTEM_ERROR;
        } finally {
//            lockService.unlock(cardNumber);
            if (serviceResponse != null) {
                return getValidationResponse(builder, serviceResponse);
            }
        }


        ProductLimitsDef productLimitsDef = productLimitsDefRegion.get(new ProductId(cardAccumulatedValues.getOrg(),
                cardAccumulatedValues.getProduct()));

        if (productLimitsDef == null) {
            return getValidationResponse(builder, ServiceResponse.NO_PRODUCT);
        }
        boolean initializeDaily = false;
        boolean initializeMonth = false;
        boolean initializeYear = false;

        LocalDateTime lastUpdatedDateTime = cardAccumulatedValues.getLastUpdatedDateTime();
        LocalDateTime currentDateTime = LocalDateTime.now();

        if (lastUpdatedDateTime.getYear() == currentDateTime.getYear()) {
            if (lastUpdatedDateTime.getMonthValue() == currentDateTime.getMonthValue()) {
                if (lastUpdatedDateTime.getDayOfMonth() == currentDateTime.getDayOfMonth()) {
                } else {
                    initializeDaily = true;
                }
            } else {
                initializeMonth = true;
                initializeDaily = true;
            }

        } else {
            initializeYear = true;
            initializeMonth = true;
            initializeDaily = true;

        }


        serviceResponse = evaluateAllLimits(productLimitsDef.getCardLimitMap(), cardAccumulatedValues.getPeriodicTypePeriodicCardLimitMap(),
                cardAccumulatedValues.getPeriodicCardAccumulatedValueMap(), cardsLimitInput, cacheCardTempAccumMap,initializeDaily, initializeMonth, initializeYear);


        return builder
                .serviceResponse(serviceResponse)
                .build();


    }

    private ServiceResponse evaluateAllLimits(Map<PeriodicType, Map<LimitType, PeriodicCardAmount>> productLimitMap,
                                              Map<PeriodicType, Map<LimitType, PeriodicCardAmount>> periodicTypePeriodicCardLimitMap,
                                              Map<PeriodicType, Map<LimitType, PeriodicCardAmount>> periodicCardAccumulatedValueMap,
                                              CardsLimitInput cardsLimitInput, Map<LimitType, CacheCardTempAccum> cacheCardTempAccumMap,boolean initializeDaily,
                                              boolean initializeMonth, boolean initializeYear) {

        if(periodicCardAccumulatedValueMap == null){
            periodicCardAccumulatedValueMap = new HashMap<>();
        }
        if(periodicTypePeriodicCardLimitMap == null){
            periodicTypePeriodicCardLimitMap = new HashMap<>();
        }

        boolean checkResult = evaluatePeriodicLimit(productLimitMap.get(PeriodicType.SINGLE), periodicTypePeriodicCardLimitMap.get(PeriodicType.SINGLE),
                periodicCardAccumulatedValueMap.get(PeriodicType.SINGLE), cardsLimitInput, null, true);

        if (checkResult) {
            return ServiceResponse.CARD_LIMIT;
        }

        checkResult = evaluatePeriodicLimit(productLimitMap.get(PeriodicType.DAILY), periodicTypePeriodicCardLimitMap.get(PeriodicType.DAILY),
                periodicCardAccumulatedValueMap.get(PeriodicType.DAILY), cardsLimitInput, cacheCardTempAccumMap, initializeDaily);

        if (checkResult) {
            return ServiceResponse.CARD_LIMIT;
        }


        checkResult = evaluatePeriodicLimit(productLimitMap.get(PeriodicType.MONTHLY), periodicTypePeriodicCardLimitMap.get(PeriodicType.MONTHLY),
                periodicCardAccumulatedValueMap.get(PeriodicType.MONTHLY), cardsLimitInput, cacheCardTempAccumMap, initializeMonth);

        if (checkResult) {
            return ServiceResponse.CARD_LIMIT;
        }

        checkResult = evaluatePeriodicLimit(productLimitMap.get(PeriodicType.YEARLY), periodicTypePeriodicCardLimitMap.get(PeriodicType.YEARLY),
                periodicCardAccumulatedValueMap.get(PeriodicType.YEARLY), cardsLimitInput, cacheCardTempAccumMap, initializeYear);


        if (checkResult) {
            return ServiceResponse.CARD_LIMIT;
        }

        return ServiceResponse.OK;

    }

    private boolean evaluatePeriodicLimit(Map<LimitType, PeriodicCardAmount> productLimitMap,
                                          Map<LimitType, PeriodicCardAmount> cardLimitMap,
                                          Map<LimitType, PeriodicCardAmount> periodicCardAmountMap,
                                          CardsLimitInput cardsLimitInput,
                                          Map<LimitType, CacheCardTempAccum> cacheCardTempAccumMap, boolean initialize) {


        for (LimitType limitType : cardsLimitInput.getLimitTypes()) {
            PeriodicCardAmount cardAccumPeriodicCardAmount;
            CacheCardTempAccum cacheCardTempAccum;
            PeriodicCardAmount productPeriodicCardAmount;
            PeriodicCardAmount cardLimitPeriodic;


            if (periodicCardAmountMap == null) {
                cardAccumPeriodicCardAmount = null;
            } else {
                cardAccumPeriodicCardAmount = periodicCardAmountMap.get(limitType);
            }

            if (cacheCardTempAccumMap == null) {
                cacheCardTempAccum = null;
            } else {
                cacheCardTempAccum = cacheCardTempAccumMap.get(limitType);
            }

            if (productLimitMap == null) {
                productPeriodicCardAmount = null;
            } else {
                productPeriodicCardAmount = productLimitMap.get(limitType);
            }

            if (cardLimitMap == null) {
                cardLimitPeriodic = null;
            } else {
                cardLimitPeriodic = cardLimitMap.get(limitType);
            }


            if (cardLimitPeriodic == null && productPeriodicCardAmount == null) {

            } else {

                int transactionCount = 1;
                long transactionAmount = cardsLimitInput.getTransactionAmount();


                if (cacheCardTempAccum != null) {
                    transactionCount = cacheCardTempAccum.getAccumCount() + transactionCount;
                    transactionAmount = transactionAmount  + cacheCardTempAccum.getAccumAmount();
                }


                if (cardAccumPeriodicCardAmount != null && !initialize) {
                    transactionAmount = transactionAmount + cardAccumPeriodicCardAmount.getTransactionAmount();
                    transactionCount = transactionCount + cardAccumPeriodicCardAmount.getTransactionNumber();
                }

                if (productPeriodicCardAmount != null) {
                    if (productPeriodicCardAmount.getTransactionNumber() < transactionCount) {
                        return true;
                    }
                    if (productPeriodicCardAmount.getTransactionAmount() < transactionAmount) {
                        return true;
                    }
                }

                if (cardLimitPeriodic != null) {
                    if (cardLimitPeriodic.getTransactionNumber() < transactionCount) {
                        return true;
                    }
                    if (cardLimitPeriodic.getTransactionAmount() < transactionAmount) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    private boolean evaluateLimit(Long limit, AccountBalances accountBalances, long accumulatedBalance) {

        long balance = accountBalances.getPostedBalance() + accountBalances.getMemoDb() - accountBalances.getMemoCr() + accumulatedBalance;
        if (balance > limit) {
            return true;
        }
        return false;
    }

    private CardTempBalance createNewCardTempBalance(String cardNumber, CardsLimitInput cardsLimitInput) {

        long transactionAmount = 0L;
        if (cardsLimitInput.isDebit()) {
            transactionAmount = cardsLimitInput.getTransactionAmount();
        } else {
            transactionAmount = cardsLimitInput.getTransactionAmount() * -1;
        }

        return CardTempBalance.builder()
                .id(cardsLimitInput.getMessageId())
                .cardNumber(cardNumber)
                .amount(transactionAmount)
                .limitTypes(cardsLimitInput.getLimitTypes())
                .build();
    }

    private Map<LimitType, CacheCardTempAccum> findAccumulatedAmount(List<CardTempBalance> cardTempBalanceList) {

        Map<LimitType, CacheCardTempAccum> cacheCardTempAccumMap = new HashMap<>();

        for (CardTempBalance cardTempBalance : cardTempBalanceList) {

            cardTempBalance.getLimitTypes()
                    .forEach(limitType -> updateLimitValues(cacheCardTempAccumMap, limitType, cardTempBalance.getAmount()));

        }
        return cacheCardTempAccumMap;
    }

    private void updateLimitValues(Map<LimitType, CacheCardTempAccum> cacheCardTempAccumMap,
                                   LimitType limitType, Long amount) {

        CacheCardTempAccum cacheCardTempAccum = cacheCardTempAccumMap.get(limitType);
        if (cacheCardTempAccum == null) {
            cacheCardTempAccum = CacheCardTempAccum.builder()
                    .accumAmount(amount)
                    .accumCount(1)
                    .limitType(limitType)
                    .build();
            cacheCardTempAccumMap.put(limitType,cacheCardTempAccum);
        } else {
            cacheCardTempAccum.setAccumAmount(cacheCardTempAccum.getAccumAmount() + amount);
            cacheCardTempAccum.setAccumCount(cacheCardTempAccum.getAccumCount() + 1);
        }
    }


    private ValidationResponse getValidationResponse(ValidationResponse.ValidationResponseBuilder builder,
                                                     ServiceResponse serviceResponse) {
        return builder
                .serviceResponse(serviceResponse)
                .build();
    }

    private List<CardTempBalance> getAllCardTempBalance(String cardNumber) throws NameResolutionException, TypeMismatchException, QueryInvocationTargetException, FunctionDomainException {
        String query = "select * from /cardTempBalance cardTempBalance where cardTempBalance.cardNumber='&'".replace("&", cardNumber);
        log.info(query);
        SelectResults<CardTempBalance> selectResults = cardTempBalanceRegion.query(query);
        return selectResults.asList();

    }

}
