package com.dbshift.model;

import java.util.ArrayList;
import java.util.List;

public class SqlImpact {

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, UNKNOWN
    }

    private String statement;
    private String operation;
    private List<String> tables = new ArrayList<>();
    private RiskLevel riskLevel = RiskLevel.LOW;
    private String note;

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
