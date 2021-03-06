package org.openvasp.client.common;

/**
 * @author Olexandr_Bilovol@epam.com
 */
public class VaspException extends RuntimeException {

    public VaspException() {
    }

    public VaspException(Throwable cause) {
        super(cause);
    }

    public VaspException(String format, Object... args) {
        super(String.format(format, args));
    }

    public VaspException(Throwable cause, String format, Object... args) {
        super(String.format(format, args), cause);
    }

}
