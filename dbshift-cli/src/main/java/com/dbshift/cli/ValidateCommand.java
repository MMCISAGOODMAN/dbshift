package com.dbshift.cli;

import com.dbshift.model.ValidationResult;
import picocli.CommandLine;

@CommandLine.Command(name = "validate", description = "Validate migration scripts")
public class ValidateCommand extends DbshiftRootCommand.BaseCommand {

    @Override
    public Integer call() throws Exception {
        ValidationResult result = engine().validate();
        System.out.println("Valid: " + result.isValid());
        result.getErrors().forEach(e -> System.out.println("ERROR: " + e));
        result.getWarnings().forEach(w -> System.out.println("WARN: " + w));
        return result.isValid() ? 0 : 1;
    }
}
