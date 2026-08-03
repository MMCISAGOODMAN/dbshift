package com.dbshift.cli;

import com.dbshift.model.MigrateResult;
import picocli.CommandLine;

@CommandLine.Command(name = "migrate", description = "Apply pending migrations")
public class MigrateCommand extends DbshiftRootCommand.BaseCommand {

    @CommandLine.Option(names = {"-t", "--target"}, description = "Target version")
    private String target;

    @CommandLine.Option(names = {"--require-confirm"}, description = "Require --confirm")
    private boolean requireConfirm;

    @CommandLine.Option(names = {"--confirm"}, description = "Confirm protected execution")
    private boolean confirm;

    @CommandLine.Option(names = {"--applied-by"}, defaultValue = "cli")
    private String appliedBy;

    @Override
    public Integer call() throws Exception {
        MigrateResult result = engine().migrate(environment, target, requireConfirm, confirm, appliedBy);
        if (result.isSuccess()) {
            if (result.getExecutedVersions().isEmpty()) {
                System.out.println("No pending migrations.");
            } else {
                System.out.println("Applied: " + String.join(", ", result.getExecutedVersions()));
            }
            return 0;
        }
        System.err.println("Migration failed: " + result.getErrorMessage());
        if (result.getFailedVersion() != null) {
            System.err.println("Failed version: " + result.getFailedVersion());
        }
        return 1;
    }
}
