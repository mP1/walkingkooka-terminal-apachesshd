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

import org.junit.jupiter.api.Test;
import walkingkooka.ToStringTesting;
import walkingkooka.environment.EnvironmentContextTesting;
import walkingkooka.locale.CanLocaleForLanguageTagTesting;
import walkingkooka.reflect.PackagePrivateClassTesting;
import walkingkooka.terminal.TerminalContext;
import walkingkooka.terminal.server.FakeTerminalServerContext;

import java.util.function.BiFunction;

public final class ApacheSshdServerShellCommandTest implements CanLocaleForLanguageTagTesting,
    EnvironmentContextTesting,
    ToStringTesting<ApacheSshdServerShellCommand>,
    PackagePrivateClassTesting<ApacheSshdServerShellCommand> {

    // toString.........................................................................................................

    @Test
    public void testToString() {
        this.toStringAndCheck(
            ApacheSshdServerShellCommand.with(
                CAN_LOCALE_FOR_LANGUAGE_TAG,
                new BiFunction<String, TerminalContext, Object>() {
                    @Override
                    public Object apply(final String evaluate,
                                        final TerminalContext terminalContext) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public String toString() {
                        return "EVALUATOR";
                    }
                },
                new FakeTerminalServerContext() {
                    @Override
                    public String toString() {
                        return "TERMINAL_SERVER_CONTEXT";
                    }
                },
                ENVIRONMENT_CONTEXT
            ),
            "canLocaleForLanguageTag=CAN_LOCALE_FOR_LANGUAGE_TAG evaluator=EVALUATOR terminalServerContext=TERMINAL_SERVER_CONTEXT environmentContext={charset=UTF-8, currency=AUD, indentation=\"  \", lineEnding=\"\\n\", locale=en_AU, loggingLevel=NONE, timeOffset=Z, user=user123@example.com}"
        );
    }

    // class............................................................................................................

    @Override
    public Class<ApacheSshdServerShellCommand> type() {
        return ApacheSshdServerShellCommand.class;
    }
}
