/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GPLv3
 * See license text in LICENSE.txt
 */

package dk.dbc.rawrepo;

import dk.dbc.marc.DanMarc2Charset;
import dk.dbc.marc.reader.DanMarc2LineFormatReader;
import dk.dbc.marc.reader.Iso2709Reader;
import dk.dbc.marc.reader.LineFormatReader;
import dk.dbc.marc.reader.MarcReader;
import dk.dbc.marc.reader.MarcReaderException;
import dk.dbc.marc.reader.MarcXchangeV1Reader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class UpdateApp {
    private static final int PUSHBACK_BUFFER_SIZE = 1000;

    public static void main(String[] args) {
        try {
            runWith(args);
        } catch (CliException e) {
            System.exit(1);
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        System.exit(0);
    }

    private static void runWith(String[] args) throws CliException {
        final Cli cli = new Cli(args);

        String username = cli.args.getString("username");
        String groupId = cli.args.getString("group_id");
        String password = cli.args.getString("password");
        String template = cli.args.getString("template");
        String trackingId = cli.args.getString("tracking_id");
        Integer priority = cli.args.getInt("priority");
        String provider = cli.args.getString("provider");
        int errorLimit = cli.args.getInt("error_limit");
        String updateServiceUrl = cli.args.getString("url");
        boolean validateOnly = cli.args.get("validate_only") != null ? cli.args.get("validate_only") : true;

        final File in = cli.args.get("IN");
        final int pushbackBufferSize = (int) Math.min(in.length(), PUSHBACK_BUFFER_SIZE);

        System.out.println("***************************");
        System.out.println("* Update Service URL: " + updateServiceUrl);
        System.out.println("*           Username: " + username);
        System.out.println("*              Group: " + groupId);
        System.out.println("*           Password: " + password.substring(0, 1) + "********");
        System.out.println("*           Template: " + template);
        System.out.println("*        Tracking Id: " + trackingId);
        System.out.println("*           Priority: " + priority);
        System.out.println("*           Provider: " + provider);
        System.out.println("*      Validate Only: " + validateOnly);
        System.out.println("*        Error Limit: " + errorLimit);
        System.out.println("***************************");

        try (PushbackInputStream is = "-".equals(in.getName())
                ? new PushbackInputStream(System.in, pushbackBufferSize)
                : new PushbackInputStream(new FileInputStream((File) cli.args.get("IN")), pushbackBufferSize)) {
            final Charset inputEncoding = StandardCharsets.UTF_8;
            final MarcReader marcRecordReader = getMarcReader(cli, is, inputEncoding, pushbackBufferSize);
            final UpdateServiceHandler updateServiceHandler = new UpdateServiceHandler(username, groupId, password,
                    template, trackingId, priority, provider, validateOnly, errorLimit, updateServiceUrl);

            updateServiceHandler.run(marcRecordReader);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not read file");
        } catch (IOException e) {
            throw new RuntimeException("Could not open stream");
        } catch (MarcReaderException e) {
            throw new RuntimeException("Could not create MarcReader");
        }
    }

    private static MarcReader getMarcReader(Cli cli, PushbackInputStream is, Charset encoding, int pushbackBufferSize) throws MarcReaderException {
        final MarcFormatDeducer marcFormatDeducer = new MarcFormatDeducer(pushbackBufferSize);

        Charset sampleEncoding = encoding;
        if (encoding instanceof DanMarc2Charset) {
            // Don't complicate the format deduction
            // by introducing the DanMarc2 charset
            // into the mix.
            sampleEncoding = Charset.forName("LATIN1");
        }
        final MarcFormatDeducer.FORMAT format =
                marcFormatDeducer.deduce(is, sampleEncoding);

        if (format == MarcFormatDeducer.FORMAT.LINE
                && encoding instanceof DanMarc2Charset) {
            // For line format we need a special
            // variant of the DanMarc2 charset.
            encoding = new DanMarc2Charset(DanMarc2Charset.Variant.LINE_FORMAT);
        }

        switch (format) {
            case LINE:
                return new LineFormatReader(is, encoding)
                        .setProperty(LineFormatReader.Property.INCLUDE_WHITESPACE_PADDING,
                                cli.args.getBoolean("include_whitespace_padding"));
            case DANMARC2_LINE:
                return new DanMarc2LineFormatReader(is, encoding);
            case MARCXCHANGE:
                return new MarcXchangeV1Reader(is, encoding);
            default:
                return new Iso2709Reader(is, encoding);
        }
    }

}
