package com.compliance.checker.common.entity;

/** Outcome of a single rule evaluation or an aggregated/LLM review. */
public enum Verdict {
    PASS,
    FAIL,
    UNCERTAIN
}
