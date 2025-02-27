package com.oviva.ehealthid.cli;

import com.oviva.ehealthid.cli.forms.DeregistrationFormRenderer;
import java.net.URI;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "feddereg",
    mixinStandardHelpOptions = true,
    description =
        """
        Generate requests for de-registration in the eHealthID federation. Sent to Gematik.
        """)
public class FedDeregistrationCommand implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(FedDeregistrationCommand.class);

  @Option(
      names = {"-e", "--environment"},
      description =
          "the environment to register for, either TU (Testumgebung) or RU (Referenzumgebung)",
      defaultValue = "TU",
      required = true)
  private DeregistrationFormRenderer.Model.Environment environment;

  @Option(
      names = {"-m", "--member-id"},
      description = "the member ID to register for, needs to be requested from Gematik",
      required = true)
  private String memberId;

  @Option(
      names = {"-i", "--iss", "--issuer-uri"},
      description = "the issuer uri of the 'Fachdienst' identiy provider",
      required = true)
  private URI issuerUri;

  public static void main(String[] args) {

    int exitCode = 1;
    try {
      var main = new FedDeregistrationCommand();
      exitCode = new CommandLine(main).execute(args);
    } catch (Exception e) {
      logger.atError().setCause(e).log("key generator failed");
      System.exit(1);
    }
    System.exit(exitCode);
  }

  public Integer call() throws Exception {

    var form =
        DeregistrationFormRenderer.render(
            new DeregistrationFormRenderer.Model(memberId, issuerUri, environment));
    System.out.println(form);
    return 0;
  }
}
