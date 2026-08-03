package com.dbshift.model;

import java.util.ArrayList;
import java.util.List;

public class RepairResult {

    private boolean success = true;
    private final List<String> repairedChecksums = new ArrayList<>();
    private final List<String> removedFailed = new ArrayList<>();
    private String errorMessage;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<String> getRepairedChecksums() {
        return repairedChecksums;
    }

    public List<String> getRemovedFailed() {
        return removedFailed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
