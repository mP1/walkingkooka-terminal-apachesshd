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

import org.apache.sshd.common.io.DefaultIoServiceFactoryFactory;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import walkingkooka.environment.EnvironmentContext;
import walkingkooka.environment.EnvironmentContexts;
import walkingkooka.io.TextReader;
import walkingkooka.net.IpPort;
import walkingkooka.terminal.TerminalContext;
import walkingkooka.terminal.TerminalId;
import walkingkooka.terminal.server.TerminalServerContext;
import walkingkooka.terminal.server.TerminalServerContexts;
import walkingkooka.text.CharSequences;
import walkingkooka.text.Indentation;
import walkingkooka.text.printer.Printer;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Starts a Apache MINA SSHD server using a provided {@link TerminalServerContext} to create terminal sessions.
 */
public final class ApacheSshdServer {

    public static ApacheSshdServer with(final IpPort port,
                                        final BiFunction<String, String, Boolean> passwordAuthenticator,
                                        final BiFunction<String, PublicKey, Boolean> publicKeyAuthenticator,
                                        final BiFunction<String, TerminalContext, Object> evaluator,
                                        final EnvironmentContext environmentContext,
                                        final TerminalServerContext terminalServerContext) {
        return new ApacheSshdServer(
            Objects.requireNonNull(port, "port"),
            Objects.requireNonNull(passwordAuthenticator, "passwordAuthenticator"),
            Objects.requireNonNull(publicKeyAuthenticator, "publicKeyAuthenticator"),
            Objects.requireNonNull(evaluator, "evaluator"),
            Objects.requireNonNull(environmentContext, "environmentContext"),
            Objects.requireNonNull(terminalServerContext, "terminalServerContext")
        );
    }

    private ApacheSshdServer(final IpPort port,
                             final BiFunction<String, String, Boolean> passwordAuthenticator,
                             final BiFunction<String, PublicKey, Boolean> publicKeyAuthenticator,
                             final BiFunction<String, TerminalContext, Object> evaluator,
                             final EnvironmentContext environmentContext,
                             final TerminalServerContext terminalServerContext) {
        this.port = port;
        this.passwordAuthenticator = passwordAuthenticator;
        this.publicKeyAuthenticator = publicKeyAuthenticator;

        this.evaluator = evaluator;

        this.environmentContext = environmentContext;
        this.terminalServerContext = terminalServerContext;
    }

    /**
     * Prepares and starts the ssh server.
     */
    public void start() throws IOException {
        final SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(this.port.value());

        sshd.setShellFactory(
            (ChannelSession channel) -> ApacheSshdServerShellCommand.with(
                this.evaluator,
                this.terminalServerContext,
                this.environmentContext
            )
        );

        sshd.setSessionHeartbeat(
            SessionHeartbeatController.HeartbeatType.IGNORE,
            Duration.ofSeconds(5)
        );

        final SimpleGeneratorHostKeyProvider hostKeys = new SimpleGeneratorHostKeyProvider(Paths.get("/tmp/" + ApacheSshdServer.class.getSimpleName() + ".ser"));
        sshd.setKeyPairProvider(hostKeys);
        hostKeys.loadKeys(null);

        sshd.setPasswordAuthenticator(
            (username, password, session) -> this.passwordAuthenticator.apply(username, password)
        );
        sshd.setPublickeyAuthenticator(
            (username, pubkey, session) -> this.publicKeyAuthenticator.apply(username, pubkey)
        );
        sshd.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);

        sshd.setIoServiceFactoryFactory(
            DefaultIoServiceFactoryFactory.getDefaultIoServiceFactoryFactoryInstance()
        );

        sshd.start();

        this.sshd = sshd;
    }

    private final IpPort port;

    private final BiFunction<String, String, Boolean> passwordAuthenticator;

    private final BiFunction<String, PublicKey, Boolean> publicKeyAuthenticator;

    private final BiFunction<String, TerminalContext, Object> evaluator;

    /**
     * The template {@link EnvironmentContext} which shouldnt have a user, but has the system locale.
     */
    private final EnvironmentContext environmentContext;

    private final TerminalServerContext terminalServerContext;

    /**
     * Stops or shuts down the ssh server.
     */
    public void stop() throws IOException {
        this.sshd.stop();
    }

    private SshServer sshd;

    // Object...........................................................................................................

    @Override
    public String toString() {
        return this.sshd.toString();
    }

    // main.............................................................................................................

    public static void main(final String[] args) throws IOException {
        final AtomicLong nextTerminalId = new AtomicLong();

        final ApacheSshdServer server = new ApacheSshdServer(
            IpPort.with(2000),
            (u, p) -> u.length() > 0, // TODO not empty password
            (u, pk) -> false,
            (final String expression, 
             final TerminalContext terminalContext) -> {
                Objects.requireNonNull(expression, "expression");

                final TextReader input = terminalContext.input();
                final Printer printer = terminalContext.output();

                String e = expression;

                while(terminalContext.isTerminalOpen()) {
                    if(null != e) {
                        System.out.println();
                        System.out.println(
                            "input: " + CharSequences.quoteAndEscape(e)
                        );

                        switch (e) {
                            case "":
                                printer.println("Exiting...");
                                terminalContext.exitTerminal(null);
                                printer.println("isTerminalOpen: " + terminalContext.isTerminalOpen());
                                break;
                            default:
                                printer.println("echo: " + e);
                                printer.println("echo: " + e);
                                printer.flush();
                                break;
                        }
                    } else {
                        System.out.print(".");
                    }

                    e = input.readLine(50)
                        .orElse(null);
                }

                return 0;
            },
            EnvironmentContexts.map(
                EnvironmentContexts.empty(
                    Indentation.SPACES2,
                    TerminalContext.TERMINAL_LINE_ENDING,
                    Locale.forLanguageTag("en-AU"),
                    LocalDateTime::now,
                    EnvironmentContext.ANONYMOUS
                )
            ), // template EnvironmentContext
            TerminalServerContexts.basic(
                () -> TerminalId.with(
                    nextTerminalId.incrementAndGet()
                )
            )
        );
        server.start();

        final Scanner keyboard = new Scanner(System.in);

        for (; ; ) {
            final String line = keyboard.next();
            if ("Q".equals(line)) {
                break;
            }
        }

        server.stop();
        System.out.println("Stopped!");
    }
}
