package com.example.mallordercmpserver.data;

/**
 * Order status enum
 */
public enum OrderStatus {
    /** Pending payment (0) */
    PENDING(0, "Pending Payment"),

    /** Paid (1) */
    PAID(1, "Paid"),

    /** Shipped (2) */
    SHIPPED(2, "Shipped"),

    /** Completed (3) */
    COMPLETED(3, "Completed"),

    /** Cancelled (4) */
    CANCELLED(4, "Cancelled");

    private final int code;
    private final String description;

    /**
     * @param code        status code
     * @param description status label
     */
    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /** @return status code */
    public int getCode() {
        return code;
    }

    /** @return status label */
    public String getDescription() {
        return description;
    }

    /**
     * Look up an enum value by its status code.
     * @param code status code
     * @return matching enum value
     * @throws IllegalArgumentException if no value matches
     */
    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid order status code: " + code);
    }

    /** @return true if the order can still be cancelled */
    public boolean canBeCancelled() {
        return this == PENDING || this == PAID;
    }

    /** @return true if the order has reached a terminal state */
    public boolean isFinalStatus() {
        return this == COMPLETED || this == CANCELLED;
    }
}
