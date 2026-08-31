package com.commerce.identityaccess.customeraccount.exceptions;

public final class CustomerOwnershipRequiredException extends RuntimeException {
    public CustomerOwnershipRequiredException() {
        super("An active customer principal is required");
    }
}
