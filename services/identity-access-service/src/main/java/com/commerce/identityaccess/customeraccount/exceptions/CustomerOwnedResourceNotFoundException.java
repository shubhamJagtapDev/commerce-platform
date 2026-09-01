package com.commerce.identityaccess.customeraccount.exceptions;

public final class CustomerOwnedResourceNotFoundException extends RuntimeException {
    public CustomerOwnedResourceNotFoundException() {
        super("Customer-owned resource was not found");
    }
}
