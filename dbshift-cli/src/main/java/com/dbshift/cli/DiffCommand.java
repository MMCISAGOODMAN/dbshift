package com.dbshift.cli;

import com.dbshift.model.EnvironmentDiff;
import picocli.CommandLine;

@CommandLine.Command(name = "diff", description = "Compare environments (versions / schema)")
public class DiffCommand extends DbshiftRootCommand.BaseCommand {

    @CommandLine.Option(names = {"--schema-from"})
    private String schemaFrom;

    @CommandLine.Option(names = {"--schema-to"})
    private String schemaTo;

    @Override
    public Integer call() throws Exception {
        boolean includeSchema = schemaFrom != null && schemaTo != null;
        var engine = engine();
        EnvironmentDiff diff = engine.diffEnvironments(includeSchema, schemaFrom, schemaTo);
        System.out.println(engine.reports().toMarkdownDiff(diff));
        return 0;
    }
}
