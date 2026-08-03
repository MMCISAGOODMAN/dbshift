package com.dbshift.cli;

import com.dbshift.model.MigrateResult;
import com.dbshift.model.MigrationFile;
import com.dbshift.model.RollbackPlan;
import picocli.CommandLine;

@CommandLine.Command(name = "rollback", description = "Rollback migrations using U scripts")
public class RollbackCommand extends DbshiftRootCommand.BaseCommand {

    @CommandLine.Option(names = {"--steps"}, defaultValue = "1")
    private int steps;

    @CommandLine.Option(names = {"--dry-run"})
    private boolean dryRun;

    @CommandLine.Option(names = {"--i-know-what-im-doing"}, description = "Required to execute")
    private boolean confirmed;

    @CommandLine.Option(names = {"--applied-by"}, defaultValue = "cli")
    private String appliedBy;

    @Override
    public Integer call() throws Exception {
        if (dryRun) {
            RollbackPlan plan = engine().rollbackPlan(environment, steps, true);
            System.out.println("Rollback plan → " + plan.getTargetVersion());
            for (MigrationFile file : plan.getRollbackScripts()) {
                System.out.println("  " + file.getVersion() + " " + file.getDescription());
            }
            plan.getWarnings().forEach(w -> System.out.println("WARN: " + w));
            return 0;
        }
        MigrateResult result = engine().rollback(environment, steps, false, confirmed, appliedBy);
        if (result.isSuccess()) {
            System.out.println("Rolled back: " + String.join(", ", result.getExecutedVersions()));
            return 0;
        }
        System.err.println("Rollback failed: " + result.getErrorMessage());
        return 1;
    }
}
