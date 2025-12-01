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
import walkingkooka.environment.EnvironmentContexts;
import walkingkooka.terminal.TerminalContextTesting;
import walkingkooka.terminal.TerminalId;
import walkingkooka.text.LineEnding;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;

public final class ApacheSshdServerTerminalContextTest implements TerminalContextTesting<ApacheSshdServerTerminalContext> {

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
            () -> {}, // closeSession
            EnvironmentContexts.empty(
                LineEnding.CRNL,
                Locale.FRANCE,
                LocalDateTime::now,
                EnvironmentContext.ANONYMOUS
            )
        );
    }

    // class............................................................................................................

    @Override
    public Class<ApacheSshdServerTerminalContext> type() {
        return ApacheSshdServerTerminalContext.class;
    }
}
