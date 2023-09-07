package com.epam.reportportal.karate.enums;

public enum ItemLogLevelEnum {
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR");

    private final String name;

    ItemLogLevelEnum(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
