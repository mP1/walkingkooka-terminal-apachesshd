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
import walkingkooka.reflect.PublicStaticHelper;
import walkingkooka.terminal.TerminalContext;
import walkingkooka.terminal.TerminalId;
import walkingkooka.terminal.expression.TerminalExpressionEvaluationContext;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ApacheSshdTerminalContexts implements PublicStaticHelper {

    /**
     * {@see ApacheSshdServerTerminalContext}
     */
    public static TerminalContext apacheSshd(final TerminalId terminalId,
                                             final InputStream in,
                                             final OutputStream out,
                                             final OutputStream err,
                                             final Runnable closeSession,
                                             final EnvironmentContext environmentContext,
                                             final BiFunction<String, TerminalContext, Object> evaluator) {
        return ApacheSshdServerTerminalContext.with(
            terminalId,
            in,
            out,
            err,
            closeSession,
            environmentContext,
            evaluator
        );
    }

    /**
     * Stop creation
     */
    private ApacheSshdTerminalContexts() {
        throw new UnsupportedOperationException();
    }
}
