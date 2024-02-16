package com.oviva.ehealthid.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "ehealthid-cli",
    mixinStandardHelpOptions = true,
    version = "0.1",
    subcommands = {FedRegistrationCommand.class, KeyGeneratorCommand.class})
public class RootCommand {

  private static final Logger logger = LoggerFactory.getLogger(RootCommand.class);

  public static void main(String[] args) {

    int exitCode = 1;
    try {
      var main = new RootCommand();
      exitCode = new CommandLine(main).execute(args);
    } catch (Exception e) {
      logger.atError().setCause(e).log("key generator failed");
      System.exit(1);
    }
    System.exit(exitCode);
  }
}
