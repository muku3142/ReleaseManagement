package com.evision.ragservice.adapters.persistence;

final class VectorLiteral {

    private VectorLiteral() {
    }

    static String of(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(vector[i]));
        }
        return literal.append(']').toString();
    }
}
