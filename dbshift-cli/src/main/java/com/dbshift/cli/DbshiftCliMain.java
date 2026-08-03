package com.dbshift.cli;

import picocli.CommandLine;

public final class DbshiftCliMain {

    private DbshiftCliMain() {
    }

    public static void main(String[] args) {
        int code = new CommandLine(new DbshiftRootCommand()).execute(args);
        System.exit(code);
    }
}
