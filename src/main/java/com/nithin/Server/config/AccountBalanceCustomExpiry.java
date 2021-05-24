package com.nithin.Server.config;

import in.nmaloth.entity.account.AccountTempBalance;
import org.apache.geode.cache.CustomExpiry;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.Region;

    public class AccountBalanceCustomExpiry implements CustomExpiry<String, AccountTempBalance> {

    private final int timeout;

    public AccountBalanceCustomExpiry(int timeout) {
        this.timeout = timeout;
    }


    @Override
    public ExpirationAttributes getExpiry(Region.Entry<String, AccountTempBalance> entry) {
        return new ExpirationAttributes(timeout, ExpirationAction.DESTROY);
    }
}
