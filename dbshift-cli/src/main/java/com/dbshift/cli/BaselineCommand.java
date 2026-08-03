package com.dbshift.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "baseline", description = "Baseline an existing database at a given version")
public class BaselineCommand extends DbshiftRootCommand.BaseCommand {

    @CommandLine.Option(names = {"--version"}, description = "Baseline version, e.g. 1 or V003", required = true)
    private String version;

    @CommandLine.Option(names = {"--applied-by"}, description = "Recorded actor", defaultValue = "cli")
    private String appliedBy;

    @Override
    public Integer call() throws Exception {
        engine().baseline(environment, version, appliedBy);
        System.out.println("Baselined " + environment + " at " + version);
        return 0;
    }
}
