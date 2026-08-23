package com.commerce.catalog.catalogsecurity;

public final class CatalogAuthorizationException extends RuntimeException {
    public CatalogAuthorizationException() {
        super("Catalog authorization was denied");
    }
}
