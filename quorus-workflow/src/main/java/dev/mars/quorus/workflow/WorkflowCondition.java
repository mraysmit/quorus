/*
 * Copyright 2025 Mark Andrew Ray-Smith Cityline Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.mars.quorus.workflow;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A transfer group's or transfer's {@code condition} (decision ENG-Q1). The grammar is
 *
 * <pre>
 * condition := or
 * or        := and ( "or" and )*
 * and       := not ( "and" not )*
 * not       := "not" not | primary
 * primary   := "(" condition ")"
 *            | "success" "(" name ")"
 *            | "failure" "(" name ")"
 *            | "file_exists" "(" quoted-path ")"
 * name      := letters, digits, '-', '_' and '.'
 * quoted-path := a path in single or double quotes, without that quote inside
 * </pre>
 *
 * <p>Keywords are lower case, and whitespace is free. {@code not} binds tightest, then {@code and},
 * then {@code or}. A variable reference ({@code {{name}}}) is allowed only inside a quoted path, so the
 * names a condition refers to are known when the workflow is validated.</p>
 *
 * <p>{@code success(x)} is true when the group or transfer {@code x} ran and succeeded;
 * {@code failure(x)} is true when it ran and failed. Both are false when {@code x} was skipped.
 * {@code file_exists(p)} is true when the path exists on the filesystem of the engine evaluating it.</p>
 */
final class WorkflowCondition {

    /** A parsed condition. */
    sealed interface Expression permits Success, Failure, FileExists, Not, And, Or {
    }

    record Success(String name) implements Expression {
    }

    record Failure(String name) implements Expression {
    }

    record FileExists(String path) implements Expression {
    }

    record Not(Expression operand) implements Expression {
    }

    record And(Expression left, Expression right) implements Expression {
    }

    record Or(Expression left, Expression right) implements Expression {
    }

    /** A group or transfer that a condition names, and whether it is named by {@code failure(...)}. */
    record Reference(String name, boolean failure) {
    }

    /** The outcomes a condition is evaluated against. */
    interface Outcomes {
        /** True if the named group or transfer succeeded, false if it failed, null if it did not run. */
        Boolean succeeded(String name);
    }

    /** A condition that is outside the grammar, or that cannot be evaluated. */
    static final class InvalidConditionException extends IllegalArgumentException {
        InvalidConditionException(String message) {
            super(message);
        }
    }

    private WorkflowCondition() {
    }

    /** Parses {@code text}. */
    static Expression parse(String text) {
        return new Parser(text).parseCondition();
    }

    /** The groups and transfers {@code expression} names, in the order they appear. */
    static List<Reference> references(Expression expression) {
        List<Reference> references = new ArrayList<>();
        collect(expression, references);
        return references;
    }

    /**
     * Evaluates {@code expression}. {@code and} and {@code or} evaluate their right side only when it
     * decides the result.
     *
     * @throws InvalidConditionException if a {@code file_exists} path is not a valid path
     */
    static boolean evaluate(Expression expression, Outcomes outcomes) {
        return switch (expression) {
            case Success success -> Boolean.TRUE.equals(outcomes.succeeded(success.name()));
            case Failure failure -> Boolean.FALSE.equals(outcomes.succeeded(failure.name()));
            case FileExists fileExists -> exists(fileExists.path());
            case Not not -> !evaluate(not.operand(), outcomes);
            case And and -> evaluate(and.left(), outcomes) && evaluate(and.right(), outcomes);
            case Or or -> evaluate(or.left(), outcomes) || evaluate(or.right(), outcomes);
        };
    }

    private static boolean exists(String path) {
        try {
            return Files.exists(Path.of(path));
        } catch (InvalidPathException e) {
            throw new InvalidConditionException("file_exists path is not a valid path: '" + path + "'");
        }
    }

    private static void collect(Expression expression, List<Reference> references) {
        switch (expression) {
            case Success success -> references.add(new Reference(success.name(), false));
            case Failure failure -> references.add(new Reference(failure.name(), true));
            case FileExists _ -> {
                // names no group or transfer
            }
            case Not not -> collect(not.operand(), references);
            case And and -> {
                collect(and.left(), references);
                collect(and.right(), references);
            }
            case Or or -> {
                collect(or.left(), references);
                collect(or.right(), references);
            }
        }
    }

    /** A recursive-descent parser over the grammar above. */
    private static final class Parser {
        private final String text;
        private int position;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        Expression parseCondition() {
            skipWhitespace();
            if (position == text.length()) {
                throw invalid("the condition is empty");
            }
            Expression expression = parseOr();
            skipWhitespace();
            if (position < text.length()) {
                throw invalid("unexpected '" + nextToken() + "' at position " + (position + 1));
            }
            return expression;
        }

        private Expression parseOr() {
            Expression left = parseAnd();
            while (acceptWord("or")) {
                left = new Or(left, parseAnd());
            }
            return left;
        }

        private Expression parseAnd() {
            Expression left = parseNot();
            while (acceptWord("and")) {
                left = new And(left, parseNot());
            }
            return left;
        }

        private Expression parseNot() {
            if (acceptWord("not")) {
                return new Not(parseNot());
            }
            return parsePrimary();
        }

        private Expression parsePrimary() {
            skipWhitespace();
            if (accept('(')) {
                Expression inner = parseOr();
                expect(')');
                return inner;
            }
            int start = position;
            String word = readWord();
            switch (word) {
                case "success", "failure" -> {
                    expect('(');
                    skipWhitespace();
                    int nameStart = position;
                    String name = readWord();
                    if (name.isEmpty()) {
                        throw invalid(word + "(...) needs the name of a group or transfer at position " + (nameStart + 1)
                                + describeVariableReference());
                    }
                    expect(')');
                    return word.equals("success") ? new Success(name) : new Failure(name);
                }
                case "file_exists" -> {
                    expect('(');
                    String path = readQuoted();
                    expect(')');
                    return new FileExists(path);
                }
                default -> {
                    position = start;
                    throw invalid("expected success(...), failure(...), file_exists('...'), not or '(' at position "
                            + (start + 1) + " but found '" + (position < text.length() ? nextToken() : "the end") + "'"
                            + describeVariableReference());
                }
            }
        }

        private String readQuoted() {
            skipWhitespace();
            if (position == text.length() || (text.charAt(position) != '\'' && text.charAt(position) != '"')) {
                throw invalid("file_exists needs a quoted path at position " + (position + 1));
            }
            char quote = text.charAt(position);
            int end = text.indexOf(quote, position + 1);
            if (end < 0) {
                throw invalid("the path that starts at position " + (position + 1) + " has no closing " + quote);
            }
            String path = text.substring(position + 1, end);
            position = end + 1;
            return path;
        }

        private String readWord() {
            int start = position;
            while (position < text.length() && isWordCharacter(text.charAt(position))) {
                position++;
            }
            return text.substring(start, position);
        }

        private boolean acceptWord(String word) {
            skipWhitespace();
            int start = position;
            if (readWord().equals(word)) {
                return true;
            }
            position = start;
            return false;
        }

        private boolean accept(char expected) {
            skipWhitespace();
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!accept(expected)) {
                throw invalid("expected '" + expected + "' at position " + (position + 1) + " but found '"
                        + (position < text.length() ? nextToken() : "the end") + "'" + describeVariableReference());
            }
        }

        private void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private String nextToken() {
            int end = position;
            while (end < text.length() && isWordCharacter(text.charAt(end))) {
                end++;
            }
            return end > position ? text.substring(position, end) : String.valueOf(text.charAt(position));
        }

        /** A hint when the parser stopped at a variable reference, which only a quoted path may hold. */
        private String describeVariableReference() {
            return text.startsWith("{{", position)
                    ? "; a variable reference is allowed only inside a file_exists path" : "";
        }

        private InvalidConditionException invalid(String problem) {
            return new InvalidConditionException(problem);
        }

        private static boolean isWordCharacter(char c) {
            return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
        }
    }
}
