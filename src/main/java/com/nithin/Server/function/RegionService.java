package com.nithin.Server.function;

import lombok.extern.slf4j.Slf4j;
import org.apache.geode.cache.GemFireCache;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RegionService implements CommandLineRunner {

    private final GemFireCache gemfireCache;

    private final AccountLimitFunction accountLimitFunction;
    private final CardLimitFunction cardLimitFunction;




    public RegionService(GemFireCache gemfireCache, AccountLimitFunction accountLimitFunction, CardLimitFunction cardLimitFunction){
        this.gemfireCache = gemfireCache;
        this.accountLimitFunction = accountLimitFunction;
        this.cardLimitFunction = cardLimitFunction;

        log.info("################# {}","Initiated Service" );
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("################ {}", "Entered the run method !!!!!!!!!");
        accountLimitFunction.checkAccountAccumRegions(gemfireCache);
        accountLimitFunction.createLockService(gemfireCache);
        cardLimitFunction.updateRegionInfo(gemfireCache);
        cardLimitFunction.createLockService(gemfireCache);


    }
}
