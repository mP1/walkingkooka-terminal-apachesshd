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
import walkingkooka.io.TextReader;
import walkingkooka.io.TextReaders;
import walkingkooka.net.email.EmailAddress;
import walkingkooka.terminal.TerminalContext;
import walkingkooka.terminal.TerminalId;
import walkingkooka.terminal.expression.TerminalExpressionEvaluationContext;
import walkingkooka.text.HasLineEnding;
import walkingkooka.text.printer.Printer;
import walkingkooka.text.printer.Printers;
import walkingkooka.util.OpenChecker;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link TerminalContext} for Apache SSHD.
 */
final class ApacheSshdServerTerminalContext implements TerminalContext {

    static ApacheSshdServerTerminalContext with(final TerminalId terminalId,
                                                final InputStream in,
                                                final OutputStream out,
                                                final OutputStream err,
                                                final Runnable closeSession,
                                                final EnvironmentContext environmentContext,
                                                final BiFunction<TerminalContext, EnvironmentContext, TerminalExpressionEvaluationContext> expressionEvaluationContextFactory) {
        return new ApacheSshdServerTerminalContext(
            Objects.requireNonNull(terminalId, "terminalId"),
            Objects.requireNonNull(in, "in"),
            Objects.requireNonNull(out, "out"),
            Objects.requireNonNull(err, "err"),
            Objects.requireNonNull(closeSession, "closeSession"),
            Objects.requireNonNull(environmentContext, "environmentContext"),
            Objects.requireNonNull(expressionEvaluationContextFactory, "expressionEvaluationContextFactory")
        );
    }

    private ApacheSshdServerTerminalContext(final TerminalId terminalId,
                                            final InputStream in,
                                            final OutputStream out,
                                            final OutputStream err,
                                            final Runnable closeSession,
                                            final EnvironmentContext environmentContext,
                                            final BiFunction<TerminalContext, EnvironmentContext, TerminalExpressionEvaluationContext> expressionEvaluationContextFactory) {
        super();

        this.terminalId = terminalId;

        this.input = TextReaders.reader(
            new InputStreamReader(in),
            this::echo
        );

        this.output = printer(
            out,
            environmentContext
        );

        this.error = printer(
            err,
            environmentContext
        );

        this.closeSession = closeSession;
        this.environmentContext = environmentContext;

        this.expressionEvaluationContextFactory = expressionEvaluationContextFactory;
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

    private void echo(final Character c) {
        final Printer printer = this.output;
        printer.print(c.toString());
        printer.flush(); // flush required!
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
    public TerminalContext exitTerminal() {
        this.openChecker.check();
        this.closeSession.run();
        this.openChecker.close();
        return this;
    }

    private final Runnable closeSession;

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
    public TerminalExpressionEvaluationContext terminalExpressionEvaluationContext() {
        this.openChecker.check();
        return this.expressionEvaluationContextFactory.apply(
            this,
            this.environmentContext
        );
    }

    private final BiFunction<TerminalContext, EnvironmentContext, TerminalExpressionEvaluationContext> expressionEvaluationContextFactory;

    private final OpenChecker<IllegalStateException> openChecker = OpenChecker.with(
        "Terminal closed",
        (String message) -> new IllegalStateException(message)
    );

    // EnvironmentContext...............................................................................................

    @Override
    public Optional<EmailAddress> user() {
        return this.environmentContext.user();
    }

    private final EnvironmentContext environmentContext;

    // Object...........................................................................................................

    @Override
    public String toString() {
        return this.terminalId.toString();
    }
}
