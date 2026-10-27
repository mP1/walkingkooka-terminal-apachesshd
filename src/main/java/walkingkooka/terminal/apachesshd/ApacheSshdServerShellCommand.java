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
import walkingkooka.ToStringBuilder;
import walkingkooka.UsesToStringBuilder;
import walkingkooka.environment.EnvironmentContext;
import walkingkooka.locale.CanLocaleForLanguageTag;
import walkingkooka.locale.LocaleLanguageTag;
import walkingkooka.net.email.EmailAddress;
import walkingkooka.predicate.Predicates;
import walkingkooka.storage.StorageEnvironmentContext;
import walkingkooka.storage.StorageEnvironmentContexts;
import walkingkooka.storage.StorageName;
import walkingkooka.storage.StoragePath;
import walkingkooka.terminal.TerminalContext;
import walkingkooka.terminal.TerminalId;
import walkingkooka.terminal.expression.TerminalExpressionEvaluationContext;
import walkingkooka.terminal.server.TerminalServerContext;
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * A {@link Command} that creates a new {@link TerminalExpressionEvaluationContext} assuming that starts a shell.
 */
final class ApacheSshdServerShellCommand implements Command,
    ServerChannelSessionHolder,
    ServerSessionAware,
    ServerSessionHolder,
    SessionHolder<ServerSession>,
    UsesToStringBuilder {

    static ApacheSshdServerShellCommand with(final CanLocaleForLanguageTag canLocaleForLanguageTag,
                                             final BiFunction<String, TerminalContext, Object> evaluator,
                                             final TerminalServerContext terminalServerContext,
                                             final EnvironmentContext environmentContext) {
        return new ApacheSshdServerShellCommand(
            canLocaleForLanguageTag,
            evaluator,
            terminalServerContext,
            environmentContext
        );
    }

    private ApacheSshdServerShellCommand(final CanLocaleForLanguageTag canLocaleForLanguageTag,
                                         final BiFunction<String, TerminalContext, Object> evaluator,
                                         final TerminalServerContext terminalServerContext,
                                         final EnvironmentContext environmentContext) {
        super();

        this.canLocaleForLanguageTag = canLocaleForLanguageTag;
        this.evaluator = evaluator;
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

        final Map<String, String> env = environment.getEnv();

        String loginUser = env.get(Environment.ENV_USER);
        if (null != loginUser) {
            try {
                userEmail = EmailAddress.parse(loginUser);
            } catch (final RuntimeException cause) {
                message = cause.getMessage();
            }
        } else {
            message = "Missing " + CharSequences.quoteAndEscape(Environment.ENV_USER) + " from environment";
        }

        final String lang = env.get("LANG");
        Charset charset = null;
        Locale locale = null;

        if(null == message) {
            if (null != lang) {
                // LANG=en_US.UTF-8 sublime_text
                final int dot = lang.indexOf('.');
                final String localeString = -1 == dot ?
                    lang :
                    lang.substring(
                        0,
                        dot
                    );

                try {
                    locale = this.canLocaleForLanguageTag.localeForLanguageTagOrFail(
                        LocaleLanguageTag.parse(
                            localeString.replace('_', '-')
                        )
                    );
                } catch (final UnsupportedOperationException rethrow) {
                    throw rethrow;
                } catch (final RuntimeException cause) {
                    message = cause.getMessage();
                }

                if(-1 != dot) {
                    try {
                        charset = Charset.forName(
                            lang.substring(
                                dot + 1
                            )
                        );
                    } catch (final UnsupportedOperationException rethrow) {
                        throw rethrow;
                    } catch (final RuntimeException cause) {
                        message = cause.getMessage();
                    }
                }
            }
        }

        final EnvironmentContext environmentContext = this.environmentContext;

        if (null != message) {
            this.err.write(
                message.concat(environmentContext.lineEnding().toString())
                    .getBytes(environmentContext.charset())
            );
            this.err.flush();
            channelSession.close();
        } else {
            final EmailAddress finalUserEmail = userEmail;
            final Optional<Charset> finalCharset = Optional.ofNullable(charset);
            final Optional<Locale> finalLocale = Optional.ofNullable(locale);

            final TerminalContext terminalContext = this.terminalServerContext.addTerminalContext(
                (terminalId) -> this.createTerminalContext(
                    terminalId,
                    finalUserEmail,
                    finalCharset,
                    finalLocale
                )
            );

            this.terminalContext = terminalContext;

            final Thread thread = new Thread(
                () -> {
                    try {
                        terminalContext.evaluate("shell()"); // for now leading equals sign is required
                    } finally {
                        ApacheSshdServerShellCommand.this.exitTerminal(null);
                    }
                }
            );
            thread.setUncaughtExceptionHandler(
                (final Thread t, final Throwable cause) -> {
                    final PrintWriter printWriter = new PrintWriter(this.err);
                    cause.printStackTrace(printWriter);
                    printWriter.flush();
                    printWriter.close();
                }
            );
            thread.setName(TerminalContext.class.getSimpleName() + "-shell-" + terminalContext.terminalId());
            thread.start();
        }
    }

    private final CanLocaleForLanguageTag canLocaleForLanguageTag;

    private TerminalContext createTerminalContext(final TerminalId terminalId,
                                                  final EmailAddress user,
                                                  final Optional<Charset> charset,
                                                  final Optional<Locale> locale) {
        final StorageEnvironmentContext storageEnvironmentContext = StorageEnvironmentContexts.basic(
            this.environmentContext.cloneEnvironment()
        );
        storageEnvironmentContext.setUser(
            Optional.of(user)
        );
        storageEnvironmentContext.setHomeDirectory(
            Optional.of(
                StoragePath.USERS_DIRECTORY_PREFIX.append(
                    StorageName.with(user.value())
                )
            )
        );

        // replace charset and locale if given
        if (charset.isPresent()) {
            storageEnvironmentContext.setCharset(
                charset.get()
            );
        }

        if (locale.isPresent()) {
            storageEnvironmentContext.setLocale(
                locale.get()
            );
        }

        return ApacheSshdServerTerminalContext.with(
            terminalId,
            this.in,
            this.out,
            this.err,
            this::exitTerminal,
            StorageEnvironmentContexts.readOnly(
                Predicates.is(EnvironmentContext.USER), // prevent changes to "user"
                storageEnvironmentContext
            ),
            this.evaluator
        );
    }

    // TODO ignore exitValue for now
    private void exitTerminal(final Object exitValue) {
        try {
            this.channelSession.close(); // closing channel doesnt actually disconnect remote user
            this.channelSession.getSession()
                .close();
        } catch (final IOException cause) {
            throw new RuntimeException(cause);
        }
    }

    private final TerminalServerContext terminalServerContext;

    private final EnvironmentContext environmentContext;

    private final BiFunction<String, TerminalContext, Object> evaluator;

    @Override
    public void destroy(final ChannelSession channel) {
        final TerminalContext terminalContext = this.terminalContext;
        if (null != terminalContext) {
            this.terminalContext = null;
            terminalContext.exitTerminal(null);
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

    // Object...........................................................................................................

    @Override
    public String toString() {
        return ToStringBuilder.buildFrom(this);
    }

    // UsesToStringBuilder..............................................................................................

    @Override
    public void buildToString(final ToStringBuilder builder) {
        builder.label("canLocaleForLanguageTag")
            .value(this.canLocaleForLanguageTag)
            .label("evaluator")
            .value(this.evaluator)
            .label("terminalServerContext")
            .value(this.terminalServerContext)
            .label("environmentContext")
            .value(this.environmentContext);
    }
}
