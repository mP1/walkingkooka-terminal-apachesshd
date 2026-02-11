/*
 * Copyright 2025 Miroslav Pokorny (github.com/mP1)
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
 *
 */

package walkingkooka.terminal.apachesshd;

import walkingkooka.environment.EnvironmentContext;
import walkingkooka.environment.EnvironmentContextDelegator;
import walkingkooka.io.TextReader;
import walkingkooka.io.TextReaders;
import walkingkooka.terminal.TerminalContext;
import walkingkooka.terminal.TerminalId;
import walkingkooka.text.HasLineEnding;
import walkingkooka.text.LineEnding;
import walkingkooka.text.printer.Printer;
import walkingkooka.text.printer.Printers;
import walkingkooka.util.OpenChecker;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A {@link TerminalContext} for Apache SSHD.
 */
final class ApacheSshdServerTerminalContext implements TerminalContext,
    EnvironmentContextDelegator {

    static ApacheSshdServerTerminalContext with(final TerminalId terminalId,
                                                final InputStream in,
                                                final OutputStream out,
                                                final OutputStream err,
                                                final Consumer<Object> exitValue,
                                                final EnvironmentContext environmentContext,
                                                final BiFunction<String, TerminalContext, Object> evaluator) {
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        Objects.requireNonNull(exitValue, "exitValue");
        Objects.requireNonNull(environmentContext, "environmentContext");
        Objects.requireNonNull(evaluator, "evaluator");

        final Printer output = printer(
            out,
            environmentContext
        );

        return new ApacheSshdServerTerminalContext(
            terminalId,
            TextReaders.reader(
                new InputStreamReader(in),
                new Consumer<>() {
                    @Override
                    public void accept(final Character character) {
                        final char c = character.charValue();

                        final String print;

                        switch (c) {
                            case '\n':
                                if (this.skipNextLf) {
                                    print = null;
                                    break;
                                } else {
                                    print = LineEnding.NL.toString();
                                }
                                this.skipNextLf = false;
                                break;
                            case '\r':
                                print = TERMINAL_LINE_ENDING.toString();
                                this.skipNextLf = true;
                                break;
                            case 127:
                                // print backspace to "move" cursor back over the previous character
                                print = "\u0008";
                                break;
                            default:
                                print = character.toString();
                                this.skipNextLf = false;
                                break;
                        }

                        if (null != print) {
                            output.print(print);
                            output.flush(); // flush required!
                        }
                    }

                    private boolean skipNextLf = false;
                }
            ),
            output,
            printer(
                err,
                environmentContext
            ),
            exitValue,
            OpenChecker.with(
                "Terminal closed",
                IllegalStateException::new
            ),
            environmentContext,
            evaluator
        );
    }

    private static Printer printer(final OutputStream outputStream,
                                   final HasLineEnding lineEnding) {
        return Printers.writer(
            new BufferedWriter(
                new OutputStreamWriter(outputStream)
            ),
            lineEnding
        );
    }

    private ApacheSshdServerTerminalContext(final TerminalId terminalId,
                                            final TextReader input,
                                            final Printer output,
                                            final Printer error,
                                            final Consumer<Object> exitValue,
                                            final OpenChecker<IllegalStateException> openChecker,
                                            final EnvironmentContext environmentContext,
                                            final BiFunction<String, TerminalContext, Object> evaluator) {
        super();

        this.terminalId = terminalId;

        this.input = input;
        this.output = output;

        this.error = error;

        this.exitValue = exitValue;
        this.openChecker = openChecker;

        this.environmentContext = environmentContext;
        environmentContext.setEnvironmentValue(
            TERMINAL_ID,
            terminalId
        );

        this.evaluator = evaluator;
    }

    // TerminalContext..................................................................................................

    @Override
    public TerminalId terminalId() {
        return this.terminalId;
    }

    private final TerminalId terminalId;

    @Override
    public boolean isTerminalOpen() {
        return false == this.openChecker.isClosed();
    }

    @Override
    public void exitTerminal(final Object exitValue) {
        this.openChecker.check();
        this.exitValue.accept(exitValue);
        this.openChecker.close();
    }

    private final Consumer<Object> exitValue;

    @Override
    public TextReader input() {
        this.openChecker.check();

        return this.input;
    }

    private final TextReader input;

    @Override
    public Printer output() {
        this.openChecker.check();
        return this.output;
    }

    private final Printer output;

    @Override
    public Printer error() {
        this.openChecker.check();
        return this.error;
    }

    private final Printer error;

    @Override
    public Object evaluate(final String expression) {
        Objects.requireNonNull(expression, "expression");

        this.openChecker.check();
        return this.evaluator.apply(
            expression,
            this
        );
    }

    private final BiFunction<String, TerminalContext, Object> evaluator;

    private final OpenChecker<IllegalStateException> openChecker;

    // EnvironmentContext...............................................................................................

    @Override
    public TerminalContext cloneEnvironment() {
        return this.setEnvironmentContext(
            this.environmentContext.cloneEnvironment()
        );
    }

    @Override
    public TerminalContext setEnvironmentContext(final EnvironmentContext context) {
        final EnvironmentContext before = this.environmentContext;
        final EnvironmentContext after = before.setEnvironmentContext(context);

        return before == after ?
            this :
            new ApacheSshdServerTerminalContext(
                this.terminalId,
                this.input,
                this.output,
                this.error,
                this.exitValue,
                this.openChecker,
                Objects.requireNonNull(after, "context"), // EnvironmentContext
                this.evaluator
            );
    }

    @Override
    public EnvironmentContext environmentContext() {
        return this.environmentContext;
    }

    private final EnvironmentContext environmentContext;

    // Object...........................................................................................................

    @Override
    public String toString() {
        return this.environmentContext.toString();
    }
}
