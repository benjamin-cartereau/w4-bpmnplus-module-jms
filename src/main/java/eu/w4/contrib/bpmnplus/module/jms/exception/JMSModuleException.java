package eu.w4.contrib.bpmnplus.module.jms.exception;

/**
 * Default JMS Module Exception class.
 */
public class JMSModuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    /**
     * Constructs a new module exception with the specified cause
     * @param  cause the cause 
     */
    public JMSModuleException(final Throwable cause) {
        super(cause);
    }
    
    /**
     * Constructs a new module exception with the specified detail message.
     * @param   message   the detail message.
     */
    public JMSModuleException(final String message) {
        super(message);
    }
    
    /**
     * Constructs a new module exception with the specified detail message and
     * cause.
     * @param  message the detail message.
     * @param  cause the cause.
     */
    public JMSModuleException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
