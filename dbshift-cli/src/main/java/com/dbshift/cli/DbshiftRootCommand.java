package com.dbshift.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.dbshift.DbshiftEngine;
import picocli.CommandLine;

@CommandLine.Command(name = "dbshift", subcommands = {
        InfoCommand.class,
        StatusCommand.class,
        ValidateCommand.class,
        InitCommand.class,
        BaselineCommand.class,
        PlanCommand.class,
        MigrateCommand.class,
        RepairCommand.class,
        RollbackCommand.class,
        DiffCommand.class
}, mixinStandardHelpOptions = true, version = "dbshift 1.0.0",
        description = "DbShift — lightweight database migration CLI (Flyway-inspired)")
public class DbshiftRootCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    abstract static class BaseCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"-c", "--config"}, description = "Path to dbshift.yaml")
        protected Path configFile;

        @CommandLine.Option(names = {"-e", "--env"}, description = "Environment name", defaultValue = "dev")
        protected String environment;

        protected DbshiftEngine engine() throws Exception {
            Path path = configFile != null ? configFile : Path.of("dbshift.yaml");
            return DbshiftEngine.fromYaml(path);
        }
    }
}
