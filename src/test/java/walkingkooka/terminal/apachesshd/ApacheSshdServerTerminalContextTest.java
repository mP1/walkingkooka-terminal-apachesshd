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
import walkingkooka.environment.EnvironmentValueName;
import walkingkooka.terminal.TerminalContext;
import walkingkooka.terminal.TerminalContextTesting;
import walkingkooka.terminal.TerminalId;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class ApacheSshdServerTerminalContextTest implements TerminalContextTesting<ApacheSshdServerTerminalContext>,
    ToStringTesting<ApacheSshdServerTerminalContext> {

    @Override
    public ApacheSshdServerTerminalContext createContext() {
        return ApacheSshdServerTerminalContext.with(
            TerminalId.with(1),
            new InputStream() {
                @Override
                public int read() {
                    return 0;
                }
            }, // input
            new ByteArrayOutputStream(), // output
            new ByteArrayOutputStream(), // error
            (exitValue) -> {}, // exitValue
            STORAGE_ENVIRONMENT_CONTEXT.cloneEnvironment(),
            (final String expression,
             final TerminalContext terminalContext) -> {
                throw new UnsupportedOperationException();
            }
        );
    }

    // toString.........................................................................................................

    @Test
    public void testToString() {
        final ApacheSshdServerTerminalContext context = this.createContext();
        context.setEnvironmentValue(
            EnvironmentValueName.with(
                "extra",
                Integer.class
            ),
            222
        );

        this.toStringAndCheck(
            context,
            "{charset=UTF-8, currency=AUD, currentWorkingDirectory=/current1/working2/directory3, extra=222, homeDirectory=/users/user123@example.com, indentation=\"  \", lineEnding=\"\\n\", locale=en_AU, terminal=1, timeOffset=Z, user=user123@example.com}"
        );
    }

    // class............................................................................................................

    @Override
    public Class<ApacheSshdServerTerminalContext> type() {
        return ApacheSshdServerTerminalContext.class;
    }
}
