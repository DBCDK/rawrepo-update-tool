package dk.dbc.rawrepo;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class Cli {
    Namespace args;

    Cli(String[] args) throws CliException {
        final ArgumentParser parser = ArgumentParsers.newArgumentParser("rawrepo-update-tool")
                .description("Send all records from file to updateservice.");

        parser.addArgument("-nu", "--username")
                .setDefault("netpunkt")
                .help("The username in the netpunkt triple used when calling update. Default 'netpunkt'.");

        parser.addArgument("-ng", "--group-id")
                .setDefault("010100")
                .help("The group in the netpunkt triple used when calling update. Default '010100'.");

        parser.addArgument("-np", "--password")
                .setDefault("not-used")
                .help("The password in the netpunkt triple used when calling update. \n" +
                        "Default is a dummy password because the tool is ip-validated when run on internal network.");

        parser.addArgument("-t", "--template")
                .setDefault("dbc")
                .help("The template to use when calling update. Default 'dbc'.");

        parser.addArgument("-tr", "--tracking-id")
                .help("The tracking id to use when calling update. No default tracking id.");

        parser.addArgument("-l", "--error-limit")
                .setDefault(100)
                .type(Integer.class)
                .help("The max amount of errors from updateservice before aborting the job. \n" +
                        "0 means the job is aborted on the first error. -1 means all errors are ignored. Default is 100.");

        parser.addArgument("-pi", "--priority")
                .setDefault(1000)
                .type(Integer.class)
                .help("The priority to use when calling update. Default '1000'.\n" +
                        "WARNING: Setting priority to 500 or lower will most likely affect DBCkat/Cicero.");

        parser.addArgument("-po", "--provider")
                .help("Override provider to use when calling update. If not defined updateservice chooses the provider.");

        parser.addArgument("-u", "--url")
                .required(true)
                .help("Url of the update service of the destination rawrepo. \n" +
                        "E.g. http://oss-services.dbc.dk/UpdateService/2.0");

        parser.addArgument("--validate-only")
                .type(Boolean.class)
                .nargs("?")
                .setConst(true)
                .help("Used to specify that the record should only be validated and not actually updated. \n" +
                        "Default true, so must be set to false in order to actually update the records.");

        parser.addArgument("IN")
                .type(Arguments.fileType().acceptSystemIn().verifyCanRead())
                .help("Input file or standard input if given as a dash (-). \n" +
                        "Supported formats are MARCXCHANGE, LINE or ISO2709.");

        try {
            this.args = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            throw new CliException(e);
        }
    }
}
