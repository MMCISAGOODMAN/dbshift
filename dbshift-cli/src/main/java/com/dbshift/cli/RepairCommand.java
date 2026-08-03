package com.dbshift.cli;

import com.dbshift.model.RepairResult;
import picocli.CommandLine;

@CommandLine.Command(name = "repair", description = "Align checksums and remove failed changelog rows")
public class RepairCommand extends DbshiftRootCommand.BaseCommand {

    @Override
    public Integer call() throws Exception {
        RepairResult result = engine().repair(environment);
        if (!result.isSuccess()) {
            System.err.println("Repair failed: " + result.getErrorMessage());
            return 1;
        }
        System.out.println("Repaired checksums: " + result.getRepairedChecksums());
        System.out.println("Removed failed: " + result.getRemovedFailed());
        return 0;
    }
}
