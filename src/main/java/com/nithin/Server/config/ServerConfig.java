package com.nithin.Server.config;

import in.nmaloth.entity.RegionNames;
import in.nmaloth.entity.account.AccountAccumValues;
import in.nmaloth.entity.account.AccountBasic;
import in.nmaloth.entity.account.AccountTempBalance;
import in.nmaloth.entity.card.CardAccumulatedValues;
import in.nmaloth.entity.card.CardTempBalance;
import in.nmaloth.entity.card.CardsBasic;
import in.nmaloth.entity.customer.CustomerDef;
import in.nmaloth.entity.global.CCMappingTable;
import in.nmaloth.entity.global.CurrencyKey;
import in.nmaloth.entity.global.CurrencyTable;
import in.nmaloth.entity.instrument.Instrument;
import in.nmaloth.entity.product.DeclineReasonDef;
import in.nmaloth.entity.product.ProductCardGenDef;
import in.nmaloth.entity.product.ProductDef;
import in.nmaloth.entity.product.ProductLimitsDef;
import lombok.extern.slf4j.Slf4j;
import org.apache.geode.cache.*;
import org.apache.geode.cache.configuration.RegionConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.gemfire.*;
import org.springframework.data.gemfire.config.annotation.*;
import org.springframework.data.gemfire.function.config.EnableGemfireFunctionExecutions;
import org.springframework.data.gemfire.repository.config.EnableGemfireRepositories;

@Configuration
@ComponentScan(basePackageClasses = {Instrument.class, AccountAccumValues.class, AccountBasic.class,
        CardAccumulatedValues.class, CardsBasic.class, DeclineReasonDef.class, ProductDef.class, ProductLimitsDef.class,
        ProductCardGenDef.class, CustomerDef.class,CardTempBalance.class,AccountTempBalance.class,CurrencyTable.class, CCMappingTable.class} )
@CacheServerApplication(logLevel = "error")
@EnableClusterConfiguration
@EnableEntityDefinedRegions(basePackageClasses = {Instrument.class, AccountAccumValues.class, AccountBasic.class,
        CardAccumulatedValues.class, CardsBasic.class, DeclineReasonDef.class, ProductDef.class, ProductLimitsDef.class,
        ProductCardGenDef.class, CustomerDef.class,CardTempBalance.class,AccountTempBalance.class,CurrencyTable.class,CCMappingTable.class})
@EnableExpiration
@EnableGemfireFunctionExecutions(basePackages = "com.nithin.Server.function")
@EnableIndexing
@Slf4j
public class ServerConfig {

    @Bean()
    PartitionAttributesFactoryBean<?, ?> partitionAttributes() {

        PartitionAttributesFactoryBean<String, ?> partitionAttributesFactoryBean = new PartitionAttributesFactoryBean<>();
        partitionAttributesFactoryBean.setTotalNumBuckets(11);
        partitionAttributesFactoryBean.setRedundantCopies(0);
        return partitionAttributesFactoryBean;

    }

//    @Bean("accountTempAttr")
//    PartitionAttributesFactoryBean<String,AccountTempBalance> partitionAttributesAccountTemp() {
//
//        PartitionAttributesFactoryBean<String, AccountTempBalance> partitionAttributesFactoryBean = new PartitionAttributesFactoryBean<>();
//        partitionAttributesFactoryBean.setTotalNumBuckets(11);
//        partitionAttributesFactoryBean.setRedundantCopies(0);
//        partitionAttributesFactoryBean.setColocatedWith("accountAccum");
//        return partitionAttributesFactoryBean;
//
//    }

//    @Bean("cardTempAttr")
//    PartitionAttributesFactoryBean<String,CardTempBalance> partitionAttributesCardTemp() {
//
//        PartitionAttributesFactoryBean<String, CardTempBalance> partitionAttributesFactoryBean = new PartitionAttributesFactoryBean<>();
//        partitionAttributesFactoryBean.setTotalNumBuckets(11);
//        partitionAttributesFactoryBean.setRedundantCopies(0);
//        partitionAttributesFactoryBean.setColocatedWith("cardAccum");
//        return partitionAttributesFactoryBean;
//
//    }


    @Bean("attrInstrument")
    RegionAttributesFactoryBean<String, Instrument> regionAttributesInstruments(PartitionAttributes<?, ?> partitionAttributes) {
        RegionAttributesFactoryBean<String, Instrument> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributes);
        return regionAttributesFactoryBean;
    }


    @Bean("instrument")
    PartitionedRegionFactoryBean<String,Instrument> partitionRegionInstruments(GemFireCache gemfireCache,
                                                                   @Qualifier("attrInstrument") RegionAttributes<String,Instrument> regionAttributesInstrument){

        PartitionedRegionFactoryBean<String,Instrument> partitionedRegionFactoryBean = new PartitionedRegionFactoryBean<>();
        partitionedRegionFactoryBean.setCache(gemfireCache);
        partitionedRegionFactoryBean.setRegionName(RegionNames.INSTRUMENT);
        partitionedRegionFactoryBean.setDataPolicy(DataPolicy.PARTITION);
        partitionedRegionFactoryBean.setAttributes(regionAttributesInstrument);
        return partitionedRegionFactoryBean;

    }




    @Bean("attrAccountAccum")
    RegionAttributesFactoryBean<String, AccountAccumValues> regionAttributesAccountAccum(PartitionAttributes<?, ?> partitionAttributes) {
        RegionAttributesFactoryBean<String, AccountAccumValues> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributes);
        return regionAttributesFactoryBean;
    }


    @Bean("accountAccum")
    PartitionedRegionFactoryBean<String,AccountAccumValues> partitionAccountAccum(GemFireCache gemfireCache,
                                                                              @Qualifier("attrAccountAccum") RegionAttributes<String,AccountAccumValues> regionAttributesAccountAccum){

        PartitionedRegionFactoryBean<String,AccountAccumValues> partitionedRegionFactoryBean = new PartitionedRegionFactoryBean<>();
        partitionedRegionFactoryBean.setCache(gemfireCache);
        partitionedRegionFactoryBean.setRegionName(RegionNames.ACCOUNT_LIMIT);
        partitionedRegionFactoryBean.setDataPolicy(DataPolicy.PARTITION);
        partitionedRegionFactoryBean.setAttributes(regionAttributesAccountAccum);
        return partitionedRegionFactoryBean;

    }

    @Bean("attrAccountTempBalance")
    RegionAttributesFactoryBean<String, AccountTempBalance> regionAttributesAccountTempBalance() {

        RegionAttributesFactoryBean<String, AccountTempBalance> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
        regionAttributesFactoryBean.setScope(Scope.DISTRIBUTED_NO_ACK);
        regionAttributesFactoryBean.setIndexUpdateType(IndexMaintenancePolicyType.ASYNCHRONOUS);
        return regionAttributesFactoryBean;
    }


    @Bean("accountTempBalance")
    ReplicatedRegionFactoryBean<String,AccountTempBalance> partitionAccountTempBalance(GemFireCache gemfireCache,
                                                                                  @Qualifier("attrAccountTempBalance") RegionAttributes<String,AccountTempBalance> regionAttributesAccountAccum){

        ReplicatedRegionFactoryBean<String,AccountTempBalance> replicatedRegionFactoryBean = new ReplicatedRegionFactoryBean<>();
        replicatedRegionFactoryBean.setCache(gemfireCache);
        replicatedRegionFactoryBean.setRegionName(RegionNames.ACCT_TEMP_BALANCE);
        replicatedRegionFactoryBean.setDataPolicy(DataPolicy.REPLICATE);
        replicatedRegionFactoryBean.setEntryTimeToLive(new ExpirationAttributes(15,ExpirationAction.DESTROY));      ;
        replicatedRegionFactoryBean.setAttributes(regionAttributesAccountAccum);
        return replicatedRegionFactoryBean;

    }




    @Bean("attrAccountBasic")
    RegionAttributesFactoryBean<String, AccountBasic> regionAttributesAccountBasic(PartitionAttributes<?, ?> partitionAttributes) {
        RegionAttributesFactoryBean<String, AccountBasic> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributes);
        return regionAttributesFactoryBean;
    }


    @Bean("accountBasic")
    PartitionedRegionFactoryBean<String,AccountBasic> partitionAccountBasic(GemFireCache gemfireCache,
                                                                              @Qualifier("attrAccountBasic") RegionAttributes<String,AccountBasic> regionAttributesAccountBasic){

        PartitionedRegionFactoryBean<String,AccountBasic> partitionedRegionFactoryBean = new PartitionedRegionFactoryBean<>();
        partitionedRegionFactoryBean.setCache(gemfireCache);
        partitionedRegionFactoryBean.setRegionName(RegionNames.ACCOUNT_BASIC);
        partitionedRegionFactoryBean.setDataPolicy(DataPolicy.PARTITION);
        partitionedRegionFactoryBean.setAttributes(regionAttributesAccountBasic);
        return partitionedRegionFactoryBean;

    }

    @Bean()
    @Primary
    RegionAttributesFactoryBean<?, ?> regionAttributes(PartitionAttributes<?, ?> partitionAttributes) {
        RegionAttributesFactoryBean<?, ?> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributes);
        return regionAttributesFactoryBean;
    }

    @Bean("attrCardAccum")
    RegionAttributesFactoryBean<String, CardAccumulatedValues> regionAttributesCardAccum(PartitionAttributes<?, ?> partitionAttributes) {
        RegionAttributesFactoryBean<String, CardAccumulatedValues> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributes);
        return regionAttributesFactoryBean;
    }


    @Bean("cardAccum")
    PartitionedRegionFactoryBean<String,CardAccumulatedValues> partitionCardAccum(GemFireCache gemfireCache,
                                                                   @Qualifier("attrCardAccum") RegionAttributes<String,CardAccumulatedValues> regionAttributesCardAccum){

        PartitionedRegionFactoryBean<String,CardAccumulatedValues> partitionedRegionFactoryBean = new PartitionedRegionFactoryBean<>();
        partitionedRegionFactoryBean.setCache(gemfireCache);
        partitionedRegionFactoryBean.setRegionName(RegionNames.CARD_LIMIT);
        partitionedRegionFactoryBean.setDataPolicy(DataPolicy.PARTITION);
        partitionedRegionFactoryBean.setAttributes(regionAttributesCardAccum);
        return partitionedRegionFactoryBean;

    }

    @Bean("attrCardTempBalance")
    RegionAttributesFactoryBean<String, CardTempBalance> regionAttributesCardTempBalance() {
        RegionAttributesFactoryBean<String, CardTempBalance> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
        regionAttributesFactoryBean.setScope(Scope.DISTRIBUTED_NO_ACK);
        regionAttributesFactoryBean.setIndexUpdateType(IndexMaintenancePolicyType.ASYNCHRONOUS);
        return regionAttributesFactoryBean;
    }


    @Bean("cardTempBalance")
    ReplicatedRegionFactoryBean<String,CardTempBalance> partitionCardTempBalance(GemFireCache gemfireCache,
                                                                                 @Qualifier("attrCardTempBalance") RegionAttributes<String, CardTempBalance> regionAttributes){

        ReplicatedRegionFactoryBean<String,CardTempBalance> replicatedRegionFactoryBean = new ReplicatedRegionFactoryBean<>();
        replicatedRegionFactoryBean.setCache(gemfireCache);
        replicatedRegionFactoryBean.setRegionName(RegionNames.CARD_TEMP_BALANCE);
        replicatedRegionFactoryBean.setDataPolicy(DataPolicy.REPLICATE);
        replicatedRegionFactoryBean.setRegionTimeToLive(new ExpirationAttributes(15,ExpirationAction.DESTROY));
        replicatedRegionFactoryBean.setAttributes(regionAttributes);
//        partitionedRegionFactoryBean.setEntryTimeToLive(new ExpirationAttributes(15,ExpirationAction.DESTROY));
        return replicatedRegionFactoryBean;

    }

//    @Bean
//    PartitionAttributesFactoryBean<String, CardsBasic> partitionAttributesCardsBasic() {
//
//        PartitionAttributesFactoryBean<String, CardsBasic> partitionAttributesFactoryBean = new PartitionAttributesFactoryBean<>();
//        partitionAttributesFactoryBean.setTotalNumBuckets(11);
//        partitionAttributesFactoryBean.setRedundantCopies(1);
//        return partitionAttributesFactoryBean;
//
//    }


    @Bean("attrCardsBasic")
    RegionAttributesFactoryBean<String, CardsBasic> regionAttributesCardsBasic(PartitionAttributes<?, ?> partitionAttributes) {
        RegionAttributesFactoryBean<String, CardsBasic> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributes);
        return regionAttributesFactoryBean;
    }


    @Bean("cardsBasic")
    PartitionedRegionFactoryBean<String,CardsBasic> partitionCardsBasic(GemFireCache gemfireCache,
                                                                             @Qualifier("attrCardsBasic")RegionAttributes<String,CardsBasic> regionAttributesCardsBasic){

        PartitionedRegionFactoryBean<String,CardsBasic> partitionedRegionFactoryBean = new PartitionedRegionFactoryBean<>();
        partitionedRegionFactoryBean.setCache(gemfireCache);
        partitionedRegionFactoryBean.setRegionName(RegionNames.CARDS_BASIC);
        partitionedRegionFactoryBean.setDataPolicy(DataPolicy.PARTITION);
        partitionedRegionFactoryBean.setAttributes(regionAttributesCardsBasic);
        return partitionedRegionFactoryBean;

    }

//    @Bean
//    PartitionAttributesFactoryBean<String, CustomerDef> partitionAttributesCustomerDef() {
//
//        PartitionAttributesFactoryBean<String, CustomerDef> partitionAttributesFactoryBean = new PartitionAttributesFactoryBean<>();
//        partitionAttributesFactoryBean.setTotalNumBuckets(11);
//        partitionAttributesFactoryBean.setRedundantCopies(1);
//        return partitionAttributesFactoryBean;
//
//    }


    @Bean("attrCustomerDef")
    RegionAttributesFactoryBean<String, CustomerDef> regionAttributesCustomerDef(PartitionAttributes<?, ?> partitionAttributes) {
        RegionAttributesFactoryBean<String, CustomerDef> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributes);
        return regionAttributesFactoryBean;
    }


    @Bean("customerDef")
    PartitionedRegionFactoryBean<String,CustomerDef> partitionCustomerDef(GemFireCache gemfireCache,
                                                                       @Qualifier("attrCustomerDef") RegionAttributes<String,CustomerDef> regionAttributesCustomerDef){

        PartitionedRegionFactoryBean<String,CustomerDef> partitionedRegionFactoryBean = new PartitionedRegionFactoryBean<>();
        partitionedRegionFactoryBean.setCache(gemfireCache);
        partitionedRegionFactoryBean.setRegionName(RegionNames.CUSTOMER_DEF);
        partitionedRegionFactoryBean.setDataPolicy(DataPolicy.PARTITION);
        partitionedRegionFactoryBean.setAttributes(regionAttributesCustomerDef);
        return partitionedRegionFactoryBean;

    }


//    @Bean
//    PartitionAttributesFactoryBean<String, DeclineReasonDef> partitionAttributesDeclineReason() {
//
//        PartitionAttributesFactoryBean<String, DeclineReasonDef> partitionAttributesFactoryBean = new PartitionAttributesFactoryBean<>();
//        partitionAttributesFactoryBean.setTotalNumBuckets(11);
//        partitionAttributesFactoryBean.setRedundantCopies(1);
//        return partitionAttributesFactoryBean;
//
//    }

//
//    @Bean
//    RegionAttributesFactoryBean<String, DeclineReasonDef> regionAttributesDeclineReason(PartitionAttributes<?, ?> partitionAttributeDeclineReason) {
//        RegionAttributesFactoryBean<String, DeclineReasonDef> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
//        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributeDeclineReason);
//        return regionAttributesFactoryBean;
//    }


    @Bean("declineReason")
    ReplicatedRegionFactoryBean<String,DeclineReasonDef> partitionDeclineReason(GemFireCache gemfireCache){

        ReplicatedRegionFactoryBean<String,DeclineReasonDef> replicatedRegionFactoryBean = new ReplicatedRegionFactoryBean<>();
        replicatedRegionFactoryBean.setCache(gemfireCache);
        replicatedRegionFactoryBean.setRegionName(RegionNames.DECLINE_REASON);
        replicatedRegionFactoryBean.setDataPolicy(DataPolicy.REPLICATE);
//        replicatedRegionFactoryBean.setAttributes(regionAttributesDeclineReason);
        return replicatedRegionFactoryBean;

    }

//    @Bean
//    PartitionAttributesFactoryBean<String, ProductCardGenDef> partitionAttributesProductCardGen() {
//
//        PartitionAttributesFactoryBean<String, ProductCardGenDef> partitionAttributesFactoryBean = new PartitionAttributesFactoryBean<>();
//        partitionAttributesFactoryBean.setTotalNumBuckets(11);
//        partitionAttributesFactoryBean.setRedundantCopies(1);
//        return partitionAttributesFactoryBean;
//
//    }


//    @Bean
//    RegionAttributesFactoryBean<String, ProductCardGenDef> regionAttributesProductCardGen(PartitionAttributes<String, ProductCardGenDef> partitionAttributeProductCardGenDef) {
//        RegionAttributesFactoryBean<String, ProductCardGenDef> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
//        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributeProductCardGenDef);
//        return regionAttributesFactoryBean;
//    }


    @Bean("productCardGen")
    ReplicatedRegionFactoryBean<String,ProductCardGenDef> partitionProductCardGen(GemFireCache gemfireCache,
                                                                                 RegionAttributes<String,ProductCardGenDef> regionAttributesProductCardGen){

        ReplicatedRegionFactoryBean<String,ProductCardGenDef> replicatedRegionFactoryBean = new ReplicatedRegionFactoryBean<>();
        replicatedRegionFactoryBean.setCache(gemfireCache);
        replicatedRegionFactoryBean.setRegionName(RegionNames.PRODUCT_CARD_GEN_DEF);
        replicatedRegionFactoryBean.setDataPolicy(DataPolicy.REPLICATE);
        return replicatedRegionFactoryBean;

    }


//    @Bean
//    PartitionAttributesFactoryBean<String, ProductDef> partitionAttributesProductDef() {
//
//        PartitionAttributesFactoryBean<String, ProductDef> partitionAttributesFactoryBean = new PartitionAttributesFactoryBean<>();
//        partitionAttributesFactoryBean.setTotalNumBuckets(11);
//        partitionAttributesFactoryBean.setRedundantCopies(1);
//        return partitionAttributesFactoryBean;
//
//    }
//
//
//    @Bean
//    RegionAttributesFactoryBean<String, ProductDef> regionAttributesProductDef(PartitionAttributes<String, ProductDef> partitionAttributeProductDef) {
//        RegionAttributesFactoryBean<String, ProductDef> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
//        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributeProductDef);
//        return regionAttributesFactoryBean;
//    }


    @Bean("productDef")
    ReplicatedRegionFactoryBean<String,ProductDef> partitionProductDef(GemFireCache gemfireCache){

        ReplicatedRegionFactoryBean<String,ProductDef> replicatedRegionFactoryBean = new ReplicatedRegionFactoryBean<>();
        replicatedRegionFactoryBean.setCache(gemfireCache);
        replicatedRegionFactoryBean.setRegionName(RegionNames.PRODUCT_DEF);
        replicatedRegionFactoryBean.setDataPolicy(DataPolicy.REPLICATE);
//        partitionedRegionFactoryBean.setAttributes(regionAttributesProductDef);
        return replicatedRegionFactoryBean;

    }


//    @Bean
//    PartitionAttributesFactoryBean<String, ProductLimitsDef> partitionAttributesProductLimitsDef() {
//
//        PartitionAttributesFactoryBean<String, ProductLimitsDef> partitionAttributesFactoryBean = new PartitionAttributesFactoryBean<>();
//        partitionAttributesFactoryBean.setTotalNumBuckets(11);
//        partitionAttributesFactoryBean.setRedundantCopies(1);
//        return partitionAttributesFactoryBean;
//
//    }
//
//
//    @Bean
//    RegionAttributesFactoryBean<String, ProductLimitsDef> regionAttributesProductLimitsDef(PartitionAttributes<String, ProductLimitsDef> partitionAttributeLimits) {
//        RegionAttributesFactoryBean<String, ProductLimitsDef> regionAttributesFactoryBean = new RegionAttributesFactoryBean<>();
//        regionAttributesFactoryBean.setPartitionAttributes(partitionAttributeLimits);
//        return regionAttributesFactoryBean;
//    }


    @Bean(RegionNames.PRODUCT_LIMITS)
    ReplicatedRegionFactoryBean<String,ProductLimitsDef> partitionProductLimitsDef(GemFireCache gemfireCache){

        ReplicatedRegionFactoryBean<String,ProductLimitsDef> replicatedRegionFactoryBean = new ReplicatedRegionFactoryBean<>();
        replicatedRegionFactoryBean.setCache(gemfireCache);
        replicatedRegionFactoryBean.setRegionName(RegionNames.PRODUCT_LIMITS);
        replicatedRegionFactoryBean.setDataPolicy(DataPolicy.REPLICATE);
//        partitionedRegionFactoryBean.setAttributes(regionAttributesProductLimits);
        return replicatedRegionFactoryBean;

    }

    @Bean(RegionNames.CURRENCY_CONVERSION_TABLE)
    ReplicatedRegionFactoryBean<CurrencyKey, CurrencyTable> partitionCurrencyConversionTable(GemFireCache gemfireCache){

        ReplicatedRegionFactoryBean<CurrencyKey,CurrencyTable> replicatedRegionFactoryBean = new ReplicatedRegionFactoryBean<>();
        replicatedRegionFactoryBean.setCache(gemfireCache);
        replicatedRegionFactoryBean.setRegionName(RegionNames.CURRENCY_CONVERSION_TABLE);
        replicatedRegionFactoryBean.setDataPolicy(DataPolicy.REPLICATE);
        replicatedRegionFactoryBean.setScope(Scope.DISTRIBUTED_ACK);
//        partitionedRegionFactoryBean.setAttributes(regionAttributesProductLimits);
        return replicatedRegionFactoryBean;

    }

    @Bean(RegionNames.CURRENCY_CODE_MAPPING)
    ReplicatedRegionFactoryBean<String, String> partitionCurrencyMappingTable(GemFireCache gemfireCache){

        ReplicatedRegionFactoryBean<String,String> replicatedRegionFactoryBean = new ReplicatedRegionFactoryBean<>();
        replicatedRegionFactoryBean.setCache(gemfireCache);
        replicatedRegionFactoryBean.setRegionName(RegionNames.CURRENCY_CODE_MAPPING);
        replicatedRegionFactoryBean.setDataPolicy(DataPolicy.REPLICATE);
        replicatedRegionFactoryBean.setScope(Scope.DISTRIBUTED_ACK);
//        partitionedRegionFactoryBean.setAttributes(regionAttributesProductLimits);
        return replicatedRegionFactoryBean;

    }

}
