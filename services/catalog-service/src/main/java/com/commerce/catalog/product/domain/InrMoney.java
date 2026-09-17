package com.commerce.catalog.product.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

public record InrMoney(BigDecimal amount) implements Comparable<InrMoney> {
    public static final String CURRENCY = "INR";
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^(0|[1-9][0-9]{0,8})(\\.[0-9]{1,2})?$");
    private static final BigDecimal MINIMUM = new BigDecimal("0.01");
    private static final BigDecimal MAXIMUM = new BigDecimal("999999999.99");

    public InrMoney {
        Objects.requireNonNull(amount, "amount");
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("INR amount must not exceed two decimal places");
        }
        if (amount.compareTo(MINIMUM) < 0 || amount.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException("INR amount is outside the supported range");
        }
    }

    public static InrMoney parse(String amount, String currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (!CURRENCY.equals(currency)) {
            throw new IllegalArgumentException("currency must be INR");
        }
        if (!AMOUNT_PATTERN.matcher(amount).matches()) {
            throw new IllegalArgumentException("amount must be a plain positive decimal string");
        }
        return new InrMoney(new BigDecimal(amount));
    }

    public String currency() {
        return CURRENCY;
    }

    public String responseAmount() {
        return amount.setScale(2).toPlainString();
    }

    @Override
    public int compareTo(InrMoney other) {
        return amount.compareTo(other.amount);
    }
}
