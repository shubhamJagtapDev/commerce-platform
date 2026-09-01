package com.commerce.identityaccess.customeraccount.exceptions;

public final class CustomerAccountBindingException extends RuntimeException {
    public CustomerAccountBindingException() {
        super("Customer account binding failed");
    }
}
