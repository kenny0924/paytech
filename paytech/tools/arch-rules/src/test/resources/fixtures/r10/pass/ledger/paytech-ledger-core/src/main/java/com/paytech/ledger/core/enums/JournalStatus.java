package com.paytech.ledger.core.enums;

/**
 * Fixture: compliant enum layouts (JavaDoc, annotations, arguments, bodies, trailing comma).
 */
public enum JournalStatus implements java.io.Serializable, Cloneable {

    /**
     * Draft, "not, yet" posted.
     */
    DRAFT("DRAFT", new String[] {"a", "b"}),

    @Deprecated
    POSTED("POSTED", new String[] {}), // trailing comment, with comma

    REVERSED("REVERSED", null) {
        @Override
        public String label() {
            return "reversed, voided";
        }
    },

    ;

    private final String code;

    JournalStatus(String code, String[] aliases) {
        this.code = code;
    }

    public String label() {
        return code;
    }

    enum Nested {

        ONE,

        TWO
    }
}
