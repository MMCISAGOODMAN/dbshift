package com.dbshift.cli;

import java.nio.file.Files;
import java.nio.file.Path;

import com.dbshift.DbshiftEngine;
import com.dbshift.model.MigrationPlan;
import picocli.CommandLine;

@CommandLine.Command(name = "plan", description = "Preview pending migrations with risk analysis")
public class PlanCommand extends DbshiftRootCommand.BaseCommand {

    @CommandLine.Option(names = {"-t", "--target"}, description = "Target version")
    private String target;

    @CommandLine.Option(names = {"-f", "--format"}, description = "md or html", defaultValue = "md")
    private String format;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output file")
    private Path output;

    @Override
    public Integer call() throws Exception {
        DbshiftEngine engine = engine();
        MigrationPlan plan = engine.plan(environment, target, true);
        String content = "html".equalsIgnoreCase(format)
                ? engine.reports().toHtmlPlan(plan)
                : engine.reports().toMarkdownPlan(plan);
        if (output == null) {
            System.out.println(content);
        } else {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.writeString(output, content);
            System.out.println("Report written to " + output.toAbsolutePath());
        }
        return 0;
    }
}
