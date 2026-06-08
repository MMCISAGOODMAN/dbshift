package com.dbshift.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import com.dbshift.model.EnvironmentDiff;
import com.dbshift.model.MigrationPlan;
import com.dbshift.model.MigrationStatus;
import com.dbshift.model.RollbackPlan;
import com.dbshift.model.SqlImpact;
import com.dbshift.model.ValidationResult;

public class ReportGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String toMarkdownPlan(MigrationPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DbShift Migration Plan\n\n");
        sb.append("- Environment: **").append(plan.getEnvironment()).append("**\n");
        sb.append("- Current version: **").append(nullSafe(plan.getCurrentVersion())).append("**\n");
        sb.append("- Target version: **").append(nullSafe(plan.getTargetVersion())).append("**\n");
        sb.append("- Generated at: ").append(FORMATTER.format(LocalDateTime.now())).append("\n\n");

        if (plan.getPending().isEmpty()) {
            sb.append("No pending migrations.\n");
            return sb.toString();
        }

        sb.append("## Pending migrations\n\n");
        for (var file : plan.getPending()) {
            sb.append("### ").append(file.getVersion()).append(" — ").append(file.getDescription()).append("\n\n");
            sb.append("```sql\n").append(file.getSql()).append("\n```\n\n");
        }

        appendImpacts(sb, plan.getImpacts());
        appendWarnings(sb, plan.getWarnings());
        return sb.toString();
    }

    public String toHtmlPlan(MigrationPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        sb.append("<title>DbShift Plan</title>");
        sb.append("<style>body{font-family:system-ui,sans-serif;max-width:960px;margin:2rem auto;padding:0 1rem;}");
        sb.append("pre{background:#f3f4f6;padding:1rem;border-radius:8px;overflow:auto;}");
        sb.append(".high{color:#b91c1c;font-weight:bold;}.medium{color:#b45309;}.warn{background:#fef3c7;padding:.75rem;border-radius:8px;}");
        sb.append("table{border-collapse:collapse;width:100%;margin:1rem 0;}th,td{border:1px solid #ddd;padding:.5rem;text-align:left;}</style></head><body>");
        sb.append("<h1>DbShift Migration Plan</h1>");
        sb.append("<p><strong>Environment:</strong> ").append(escape(plan.getEnvironment())).append("</p>");
        sb.append("<p><strong>Current:</strong> ").append(escape(nullSafe(plan.getCurrentVersion()))).append("</p>");
        sb.append("<p><strong>Target:</strong> ").append(escape(nullSafe(plan.getTargetVersion()))).append("</p>");

        if (plan.getPending().isEmpty()) {
            sb.append("<p>No pending migrations.</p></body></html>");
            return sb.toString();
        }

        for (var file : plan.getPending()) {
            sb.append("<h2>").append(escape(file.getVersion())).append(" — ")
                    .append(escape(file.getDescription())).append("</h2>");
            sb.append("<pre>").append(escape(file.getSql())).append("</pre>");
        }

        sb.append("<h2>Impact analysis</h2><table><tr><th>Operation</th><th>Tables</th><th>Risk</th><th>Note</th></tr>");
        for (SqlImpact impact : plan.getImpacts()) {
            String riskClass = impact.getRiskLevel() == SqlImpact.RiskLevel.HIGH ? "high"
                    : impact.getRiskLevel() == SqlImpact.RiskLevel.MEDIUM ? "medium" : "";
            sb.append("<tr><td>").append(escape(impact.getOperation())).append("</td><td>")
                    .append(escape(String.join(", ", impact.getTables()))).append("</td><td class=\"")
                    .append(riskClass).append("\">").append(impact.getRiskLevel()).append("</td><td>")
                    .append(escape(nullSafe(impact.getNote()))).append("</td></tr>");
        }
        sb.append("</table>");

        if (!plan.getWarnings().isEmpty()) {
            sb.append("<div class=\"warn\"><strong>Warnings</strong><ul>");
            for (String warning : plan.getWarnings()) {
                sb.append("<li>").append(escape(warning)).append("</li>");
            }
            sb.append("</ul></div>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    public String toMarkdownRollback(RollbackPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DbShift Rollback Plan\n\n");
        sb.append("- Environment: **").append(plan.getEnvironment()).append("**\n");
        sb.append("- Current version: **").append(nullSafe(plan.getCurrentVersion())).append("**\n");
        sb.append("- Target version: **").append(nullSafe(plan.getTargetVersion())).append("**\n");
        sb.append("- Steps: **").append(plan.getSteps()).append("**\n");
        sb.append("- Dry run: **").append(plan.isDryRun()).append("**\n\n");

        for (var file : plan.getRollbackScripts()) {
            sb.append("## ").append(file.getVersion()).append("\n\n");
            sb.append("```sql\n").append(file.getSql()).append("\n```\n\n");
        }
        appendImpacts(sb, plan.getImpacts());
        appendWarnings(sb, plan.getWarnings());
        return sb.toString();
    }

    public String toMarkdownStatus(MigrationStatus status) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DbShift Status — ").append(status.getEnvironment()).append("\n\n");
        sb.append("- Latest applied: **").append(nullSafe(status.getLatestAppliedVersion())).append("**\n");
        sb.append("- Pending count: **").append(status.getPending().size()).append("**\n\n");
        if (!status.getPending().isEmpty()) {
            sb.append("## Pending\n\n");
            for (var file : status.getPending()) {
                sb.append("- ").append(file.getVersion()).append(" — ").append(file.getDescription()).append("\n");
            }
        }
        if (!status.getMissingRollbackScripts().isEmpty()) {
            sb.append("\n## Missing rollback scripts\n\n");
            for (String version : status.getMissingRollbackScripts()) {
                sb.append("- ").append(version).append("\n");
            }
        }
        return sb.toString();
    }

    public String toMarkdownDiff(EnvironmentDiff diff) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DbShift Environment Diff\n\n");
        sb.append("| Environment | Applied | Pending |\n|---|---|---|\n");
        for (var env : diff.getEnvironments()) {
            sb.append("| ").append(env.getName()).append(" | ")
                    .append(nullSafe(env.getAppliedVersion())).append(" | ")
                    .append(env.getPendingVersions().stream().collect(Collectors.joining(", ")))
                    .append(" |\n");
        }
        if (!diff.getSchemaDifferences().isEmpty()) {
            sb.append("\n## Schema differences\n\n");
            for (var d : diff.getSchemaDifferences()) {
                sb.append("- **").append(d.getObjectType()).append("** `").append(d.getObjectName())
                        .append("`: ").append(d.getDifference()).append("\n");
            }
        }
        return sb.toString();
    }

    public String toMarkdownValidation(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DbShift Validation\n\n");
        sb.append("- Valid: **").append(result.isValid()).append("**\n\n");
        if (!result.getErrors().isEmpty()) {
            sb.append("## Errors\n\n");
            for (String error : result.getErrors()) {
                sb.append("- ").append(error).append("\n");
            }
        }
        if (!result.getWarnings().isEmpty()) {
            sb.append("\n## Warnings\n\n");
            for (String warning : result.getWarnings()) {
                sb.append("- ").append(warning).append("\n");
            }
        }
        return sb.toString();
    }

    private void appendImpacts(StringBuilder sb, java.util.List<SqlImpact> impacts) {
        if (impacts.isEmpty()) {
            return;
        }
        sb.append("## Impact analysis\n\n");
        for (SqlImpact impact : impacts) {
            sb.append("- **").append(impact.getOperation()).append("** [")
                    .append(impact.getRiskLevel()).append("] tables: ")
                    .append(String.join(", ", impact.getTables()));
            if (impact.getNote() != null) {
                sb.append(" — ").append(impact.getNote());
            }
            sb.append("\n");
        }
    }

    private void appendWarnings(StringBuilder sb, java.util.List<String> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        sb.append("\n## Warnings\n\n");
        for (String warning : warnings) {
            sb.append("- ").append(warning).append("\n");
        }
    }

    private String nullSafe(String value) {
        return value == null ? "(none)" : value;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
