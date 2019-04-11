/*
 * Copyright Dansk Bibliotekscenter a/s. Licensed under GPLv3
 * See license text in LICENSE.md
 */

package dk.dbc.rawrepo;

public class CliException extends RuntimeException {

    CliException(Throwable e) {
        super(e);
    }

}
