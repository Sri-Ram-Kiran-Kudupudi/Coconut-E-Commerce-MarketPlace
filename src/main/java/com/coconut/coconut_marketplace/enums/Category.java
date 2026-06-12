package com.coconut.coconut_marketplace.enums;

public enum Category {
    FRESH_COCONUT("Fresh Coconut"),
    COCONUT_OIL("Coconut Oil"),
    COCONUT_MILK("Coconut Milk"),
    COIR_PRODUCTS("Coir & Fiber Products"),
    SAPLINGS("Coconut Saplings"),
    SHELL_CHARCOAL("Shell & Charcoal"),
    COCONUT_FLOWER("Coconut Flower"),
    OTHER("Other Products");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
