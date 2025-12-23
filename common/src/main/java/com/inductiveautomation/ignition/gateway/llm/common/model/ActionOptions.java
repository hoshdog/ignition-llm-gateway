package com.inductiveautomation.ignition.gateway.llm.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Options that control action execution behavior.
 */
public final class ActionOptions {

    private final boolean dryRun;
    private final boolean force;
    private final String comment;

    @JsonCreator
    public ActionOptions(
            @JsonProperty("dryRun") Boolean dryRun,
            @JsonProperty("force") Boolean force,
            @JsonProperty("comment") String comment) {
        this.dryRun = dryRun != null ? dryRun : false;
        this.force = force != null ? force : false;
        this.comment = comment;
    }

    /**
     * If true, the action will be validated but not executed.
     * Returns what would happen without making changes.
     */
    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * If true, bypasses confirmation prompts for destructive operations.
     * Should be used with caution, especially in production.
     */
    public boolean isForce() {
        return force;
    }

    /**
     * Optional comment describing the reason for this action.
     * Included in audit logs.
     */
    public String getComment() {
        return comment;
    }

    /**
     * Creates default options (no dry run, no force, no comment).
     */
    public static ActionOptions defaults() {
        return new ActionOptions(false, false, null);
    }

    /**
     * Creates options for a dry run.
     */
    public static ActionOptions dryRun() {
        return new ActionOptions(true, false, null);
    }

    /**
     * Creates options with force enabled.
     */
    public static ActionOptions forced(String comment) {
        return new ActionOptions(false, true, comment);
    }

    @Override
    public String toString() {
        return "ActionOptions{" +
                "dryRun=" + dryRun +
                ", force=" + force +
                ", comment='" + comment + '\'' +
                '}';
    }
}
