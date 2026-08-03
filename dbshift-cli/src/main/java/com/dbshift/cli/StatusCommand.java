package com.dbshift.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "status", description = "Alias of info")
public class StatusCommand extends InfoCommand {
}
