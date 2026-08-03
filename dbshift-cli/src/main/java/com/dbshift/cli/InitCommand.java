package com.dbshift.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "init", description = "Create schema_changelog tables")
public class InitCommand extends DbshiftRootCommand.BaseCommand {

    @Override
    public Integer call() throws Exception {
        engine().init(environment);
        System.out.println("Initialized changelog for environment: " + environment);
        return 0;
    }
}
