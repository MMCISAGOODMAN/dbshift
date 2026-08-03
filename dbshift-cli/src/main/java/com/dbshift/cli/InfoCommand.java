package com.dbshift.cli;

import com.dbshift.model.ChangelogEntry;
import com.dbshift.model.MigrationFile;
import com.dbshift.model.MigrationStatus;
import picocli.CommandLine;

@CommandLine.Command(name = "info", description = "Show applied and pending migrations (Flyway info equivalent)")
public class InfoCommand extends DbshiftRootCommand.BaseCommand {

    @Override
    public Integer call() throws Exception {
        MigrationStatus status = engine().status(environment);
        System.out.println("Environment : " + status.getEnvironment());
        System.out.println("Latest      : " + status.getLatestAppliedVersion());
        System.out.println();
        System.out.println("Applied:");
        if (status.getApplied().isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (ChangelogEntry entry : status.getApplied()) {
                System.out.printf("  %-12s %-12s %s%n",
                        entry.getVersion(),
                        entry.getScriptType(),
                        entry.getDescription());
            }
        }
        System.out.println();
        System.out.println("Pending:");
        if (status.getPending().isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (MigrationFile file : status.getPending()) {
                System.out.printf("  %-12s %s%n", file.getVersion(), file.getDescription());
            }
        }
        return 0;
    }
}
