package com.epam.reportportal.karate.enums;

public enum ItemStatusEnum {
	PASSED("PASSED"),
	FAILED("FAILED"),
	SKIPPED("SKIPPED"),
	STOPPED("STOPPED"),
	RESETED("RESETED"),
	CANCELLED("CANCELLED");

	private final String name;

	ItemStatusEnum(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return this.name;
	}
}
