package ru.mephi.shortlink.core.model;

public enum LinkStatus {

    ACTIVE,

    EXPIRED,

    LIMIT_REACHED;

    public boolean isUsable() {
        return this == ACTIVE;
    }
}
