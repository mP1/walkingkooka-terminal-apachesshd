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

import org.apache.sshd.common.session.SessionHolder;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.channel.ServerChannelSessionHolder;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionAware;
import org.apache.sshd.server.session.ServerSessionHolder;
import walkingkooka.environment.EnvironmentContext;
import walkingkooka.io.TextReader;
import walkingkooka.net.email.EmailAddress;
import walkingkooka.terminal.TerminalContext;
import walkingkooka.terminal.TerminalId;
import walkingkooka.terminal.expression.TerminalExpressionEvaluationContext;
import walkingkooka.terminal.server.TerminalServerContext;
import walkingkooka.terminal.shell.TerminalShell;
import walkingkooka.terminal.shell.TerminalShellContext;
import walkingkooka.text.LineEnding;
import walkingkooka.text.printer.Printer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link Command} that prepares a new shell instance using the provided {@link TerminalShellContext} to eventually
 * execute entered commands.
 */
final class ApacheSshdServerShellCommand implements Command,
    ServerChannelSessionHolder,
    ServerSessionAware,
    ServerSessionHolder,
    SessionHolder<ServerSession> {

    static ApacheSshdServerShellCommand with(final Function<TerminalContext, TerminalExpressionEvaluationContext> expressionEvaluationContextFactory,
                                             final TerminalServerContext terminalServerContext,
                                             final EnvironmentContext environmentContext) {
        return new ApacheSshdServerShellCommand(
            expressionEvaluationContextFactory,
            terminalServerContext,
            environmentContext
        );
    }

    private ApacheSshdServerShellCommand(final Function<TerminalContext, TerminalExpressionEvaluationContext> expressionEvaluationContextFactory,
                                         final TerminalServerContext terminalServerContext,
                                         final EnvironmentContext environmentContext) {
        super();
        this.expressionEvaluationContextFactory = expressionEvaluationContextFactory;
        this.terminalServerContext = terminalServerContext;
        this.environmentContext = environmentContext;
    }

    @Override
    public void setExitCallback(final ExitCallback callback) {
        this.exitCallback = callback;
    }

    private ExitCallback exitCallback;

    @Override
    public void setErrorStream(final OutputStream err) {
        this.err = err;
    }

    private OutputStream err;

    @Override
    public void setInputStream(final InputStream in) {
        this.in = in;
    }

    private InputStream in;

    @Override
    public void setOutputStream(final OutputStream out) {
        this.out = out;
    }

    private OutputStream out;

    @Override
    public void start(final ChannelSession channelSession,
                      final Environment environment) throws IOException {
        this.channelSession = channelSession;

        EmailAddress userEmail = null;
        String message = null;

        String loginUser = environment.getEnv()
            .get(Environment.ENV_USER);
        if (null != loginUser) {
            try {
                userEmail = EmailAddress.parse(loginUser);
            } catch (final RuntimeException cause) {
                message = cause.getMessage();
            }
        } else {
            message = "Missing user from environment";
        }

        if (null != message) {
            this.out.write(
                message.concat("\n")
                    .getBytes(StandardCharsets.UTF_8)
            );
            this.out.flush();
            channelSession.close();
        } else {
            final EmailAddress finalUserEmail = userEmail;

            final TerminalContext terminalContext = this.terminalServerContext.addTerminalContext(
                (terminalId) -> this.createTerminalContext(
                    terminalId,
                    finalUserEmail
                )
            );
            this.terminalContext = terminalContext;

            new Thread(
                () -> terminalContext.terminalExpressionEvaluationContext()
                    .evaluate("shell")
            ).start();
        }
    }

    private TerminalContext createTerminalContext(final TerminalId terminalId,
                                                  final EmailAddress user) {
        return ApacheSshdServerTerminalContext.with(
            terminalId,
            this.in,
            this.out,
            this.err,
            () -> {
                try {
                    this.channelSession.close();
                } catch (final IOException cause) {
                    throw new RuntimeException(cause);
                }
            }, // closeSession
            this.environmentContext.cloneEnvironment()
                .setUser(
                    Optional.of(user)
                ).setLineEnding(LineEnding.CRNL),
            this.expressionEvaluationContextFactory
        );
    }

    private final TerminalServerContext terminalServerContext;

    private final EnvironmentContext environmentContext;

    private final Function<TerminalContext, TerminalExpressionEvaluationContext> expressionEvaluationContextFactory;

    @Override
    public void destroy(final ChannelSession channel) {
        final TerminalContext terminalContext = this.terminalContext;
        if (null != terminalContext) {
            this.terminalContext = null;
            terminalContext.exitTerminal();
        }
    }

    /**
     * The backing {@link TerminalContext}, which will be null when terminated.
     */
    private volatile TerminalContext terminalContext;

    // ServerSessionAware...............................................................................................

    @Override
    public void setSession(final ServerSession serverSession) {
        this.serverSession = serverSession;
    }

    private ServerSession serverSession;

    // ServerSessionHolder..............................................................................................

    @Override
    public ServerSession getSession() {
        return this.serverSession;
    }

    @Override
    public ServerSession getServerSession() {
        return this.serverSession;
    }

    // ServerChannelSessionHolder.......................................................................................

    @Override
    public ChannelSession getServerChannelSession() {
        return this.channelSession;
    }

    private ChannelSession channelSession;
}
