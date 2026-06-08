package com.dbshift.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.dbshift.dialect.DatabaseDialect;
import com.dbshift.model.SqlImpact;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;

public class SqlImpactAnalyzer {

    public List<SqlImpact> analyze(String sql) {
        return analyze(sql, null);
    }

    public List<SqlImpact> analyze(String sql, DatabaseDialect dialect) {
        List<SqlImpact> impacts = new ArrayList<>();
        List<String> statements = splitStatements(sql);
        for (String raw : statements) {
            String statement = raw.trim();
            if (statement.isEmpty() || statement.startsWith("--")) {
                continue;
            }
            impacts.add(analyzeStatement(statement, dialect));
        }
        return impacts;
    }

    public List<SqlImpact> analyzeMigration(String sql) {
        return analyze(sql);
    }

    public List<SqlImpact> analyzeMigration(String sql, DatabaseDialect dialect) {
        return analyze(sql, dialect);
    }

    private SqlImpact analyzeStatement(String statement, DatabaseDialect dialect) {
        SqlImpact impact = new SqlImpact();
        impact.setStatement(truncate(statement, 200));
        try {
            Statement parsed = CCJSqlParserUtil.parse(statement);
            if (parsed instanceof CreateTable createTable) {
                impact.setOperation("CREATE TABLE");
                impact.setTables(List.of(createTable.getTable().getName()));
                impact.setRiskLevel(SqlImpact.RiskLevel.LOW);
            } else if (parsed instanceof Alter) {
                impact.setOperation("ALTER");
                impact.setTables(List.of(((Alter) parsed).getTable().getName()));
                impact.setRiskLevel(SqlImpact.RiskLevel.MEDIUM);
                dialectAlterNote(dialect).ifPresent(impact::setNote);
            } else if (parsed instanceof Drop) {
                impact.setOperation("DROP");
                impact.setTables(List.of(((Drop) parsed).getName().getName()));
                impact.setRiskLevel(SqlImpact.RiskLevel.HIGH);
                impact.setNote("Destructive operation.");
            } else if (parsed instanceof Truncate) {
                impact.setOperation("TRUNCATE");
                impact.setTables(List.of(((Truncate) parsed).getTable().getName()));
                impact.setRiskLevel(SqlImpact.RiskLevel.HIGH);
                impact.setNote("Data loss risk.");
            } else if (parsed instanceof Insert) {
                impact.setOperation("INSERT");
                impact.setTables(List.of(((Insert) parsed).getTable().getName()));
                impact.setRiskLevel(SqlImpact.RiskLevel.LOW);
            } else if (parsed instanceof Update) {
                impact.setOperation("UPDATE");
                impact.setTables(List.of(((Update) parsed).getTable().getName()));
                impact.setRiskLevel(SqlImpact.RiskLevel.MEDIUM);
            } else if (parsed instanceof Delete) {
                impact.setOperation("DELETE");
                impact.setTables(List.of(((Delete) parsed).getTable().getName()));
                impact.setRiskLevel(SqlImpact.RiskLevel.MEDIUM);
            } else if (parsed instanceof CreateIndex createIndex) {
                impact.setOperation("CREATE INDEX");
                impact.setTables(List.of(createIndex.getTable().getName()));
                impact.setRiskLevel(SqlImpact.RiskLevel.LOW);
            } else {
                impact.setOperation(parsed.getClass().getSimpleName().replace("Statement", "").toUpperCase(Locale.ROOT));
                impact.setRiskLevel(SqlImpact.RiskLevel.UNKNOWN);
                impact.setNote("Unable to classify risk precisely.");
            }
        } catch (JSQLParserException e) {
            impact.setOperation(guessOperation(statement));
            impact.setTables(guessTables(statement));
            impact.setRiskLevel(guessRisk(statement));
            impact.setNote("Parse fallback: " + e.getMessage());
        }
        return impact;
    }

    private List<String> splitStatements(String sql) {
        return Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String guessOperation(String statement) {
        String upper = statement.toUpperCase(Locale.ROOT);
        if (upper.startsWith("CREATE TABLE")) return "CREATE TABLE";
        if (upper.startsWith("ALTER")) return "ALTER";
        if (upper.startsWith("DROP")) return "DROP";
        if (upper.startsWith("TRUNCATE")) return "TRUNCATE";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        return "UNKNOWN";
    }

    private List<String> guessTables(String statement) {
        String upper = statement.toUpperCase(Locale.ROOT);
        String[] tokens = upper.split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].equals("TABLE") || tokens[i].equals("INTO") || tokens[i].equals("FROM")) {
                return List.of(tokens[i + 1].replace("`", ""));
            }
        }
        return List.of();
    }

    private SqlImpact.RiskLevel guessRisk(String statement) {
        String upper = statement.toUpperCase(Locale.ROOT);
        if (upper.contains("DROP ") || upper.contains("TRUNCATE ")) {
            return SqlImpact.RiskLevel.HIGH;
        }
        if (upper.startsWith("ALTER") || upper.startsWith("UPDATE") || upper.startsWith("DELETE")) {
            return SqlImpact.RiskLevel.MEDIUM;
        }
        return SqlImpact.RiskLevel.UNKNOWN;
    }

    private java.util.Optional<String> dialectAlterNote(DatabaseDialect dialect) {
        if (dialect == null) {
            return java.util.Optional.empty();
        }
        return dialect.alterDdlNote();
    }

    private String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
