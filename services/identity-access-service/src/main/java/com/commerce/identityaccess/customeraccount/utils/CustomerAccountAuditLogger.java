package com.commerce.identityaccess.customeraccount.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Emits only fixed, reviewed event values; identity, email, tokens, and request data are never accepted. */
@Component
public final class CustomerAccountAuditLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerAccountAuditLogger.class);

    public void accountBindingAccepted() {
        LOGGER.info("security_event=CUSTOMER_ACCOUNT_BINDING outcome=ACCEPTED");
    }

    public void accountBindingRejected() {
        LOGGER.info("security_event=CUSTOMER_ACCOUNT_BINDING outcome=REJECTED");
    }

    public void ownershipRejected(OwnershipDenial denial) {
        LOGGER.info("security_event=CUSTOMER_OWNERSHIP outcome=REJECTED reason={}", denial);
    }

    public enum OwnershipDenial {
        WRONG_ACTOR,
        INCOMPLETE_SESSION,
        INACTIVE_OR_MISMATCHED_ACCOUNT,
        RESOURCE_NOT_FOUND
    }
}
