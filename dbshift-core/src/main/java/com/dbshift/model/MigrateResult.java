package com.dbshift.model;

import java.util.ArrayList;
import java.util.List;

public class MigrateResult {

    private boolean success;
    private List<String> executedVersions = new ArrayList<>();
    private String errorMessage;
    private String failedVersion;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<String> getExecutedVersions() {
        return executedVersions;
    }

    public void setExecutedVersions(List<String> executedVersions) {
        this.executedVersions = executedVersions;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getFailedVersion() {
        return failedVersion;
    }

    public void setFailedVersion(String failedVersion) {
        this.failedVersion = failedVersion;
    }
}
