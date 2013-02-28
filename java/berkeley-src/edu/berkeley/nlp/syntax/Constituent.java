package edu.berkeley.nlp.syntax;

import java.io.Serializable;

/**
 * A labeled span (start and end pair) representing a constituent tree node.
 * 
 * @author Dan Klein
 */
public class Constituent<L> implements Serializable {
    private static final long serialVersionUID = 1L;
    L label;
    int start;
    int end;

    public L getLabel() {
        return label;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int getLength() {
        return end - start + 1;
    }

    @Override
    public String toString() {
        return "<" + label + " : " + start + ", " + end + ">";
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Constituent)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        final Constituent<L> constituent = (Constituent<L>) o;

        if (end != constituent.end || start != constituent.start) {
            return false;
        }

        if (label != null ? !label.equals(constituent.label) : constituent.label != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        result = (label != null ? label.hashCode() : 0);
        result = 29 * result + start;
        result = 29 * result + end;
        return result;
    }

    public Constituent(final L label, final int start, final int end) {
        this.label = label;
        this.start = start;
        this.end = end;
    }
}
