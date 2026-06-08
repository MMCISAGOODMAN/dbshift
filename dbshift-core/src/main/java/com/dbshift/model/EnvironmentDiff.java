package com.dbshift.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentDiff {

    private List<EnvironmentVersionSummary> environments = new ArrayList<>();
    private Map<String, List<String>> pendingByEnvironment = new LinkedHashMap<>();
    private List<SchemaDifference> schemaDifferences = new ArrayList<>();

    public List<EnvironmentVersionSummary> getEnvironments() {
        return environments;
    }

    public void setEnvironments(List<EnvironmentVersionSummary> environments) {
        this.environments = environments;
    }

    public Map<String, List<String>> getPendingByEnvironment() {
        return pendingByEnvironment;
    }

    public void setPendingByEnvironment(Map<String, List<String>> pendingByEnvironment) {
        this.pendingByEnvironment = pendingByEnvironment;
    }

    public List<SchemaDifference> getSchemaDifferences() {
        return schemaDifferences;
    }

    public void setSchemaDifferences(List<SchemaDifference> schemaDifferences) {
        this.schemaDifferences = schemaDifferences;
    }

    public static class EnvironmentVersionSummary {
        private String name;
        private String appliedVersion;
        private List<String> pendingVersions = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAppliedVersion() {
            return appliedVersion;
        }

        public void setAppliedVersion(String appliedVersion) {
            this.appliedVersion = appliedVersion;
        }

        public List<String> getPendingVersions() {
            return pendingVersions;
        }

        public void setPendingVersions(List<String> pendingVersions) {
            this.pendingVersions = pendingVersions;
        }
    }

    public static class SchemaDifference {
        private String objectType;
        private String objectName;
        private String difference;
        private String sourceEnv;
        private String targetEnv;

        public String getObjectType() {
            return objectType;
        }

        public void setObjectType(String objectType) {
            this.objectType = objectType;
        }

        public String getObjectName() {
            return objectName;
        }

        public void setObjectName(String objectName) {
            this.objectName = objectName;
        }

        public String getDifference() {
            return difference;
        }

        public void setDifference(String difference) {
            this.difference = difference;
        }

        public String getSourceEnv() {
            return sourceEnv;
        }

        public void setSourceEnv(String sourceEnv) {
            this.sourceEnv = sourceEnv;
        }

        public String getTargetEnv() {
            return targetEnv;
        }

        public void setTargetEnv(String targetEnv) {
            this.targetEnv = targetEnv;
        }
    }
}
