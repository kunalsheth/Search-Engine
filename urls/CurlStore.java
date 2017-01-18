package urls;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import utils.Sleeper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Copyright 2016 Kunal Sheth
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class CurlStore implements Supplier<Pair<String, InputStream>>, Consumer<Pair<String, Process>> {
    private static final Queue<Triple<String, Process, Long>> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Runtime RUNTIME = Runtime.getRuntime();

    private static final StringBuilder EXIT_VALUES = new StringBuilder();

    private static long timeout = 25_000;
    private static int maxCurlsRunning = 250;
    private static CurlStore ourInstance = new CurlStore();

    private CurlStore() {
    }

    public static CurlStore getInstance(final int maxCurlsRunning, final long timeout) {
        CurlStore.maxCurlsRunning = maxCurlsRunning;
        CurlStore.timeout = timeout;
        return ourInstance;
    }

    public Process curl(final String url) {
        while (size() >= maxCurlsRunning) Sleeper.sleep(50);

        try {
            return RUNTIME.exec(new String[]{
                    "curl",
                    "--silent",
                    "--fail",
                    "--no-keepalive",
                    "--header", "Accept-Language: en",
                    "--max-time", String.valueOf(timeout / 1000),
                    url
            });
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getExitValues() {
        final String vals = EXIT_VALUES.toString();
        EXIT_VALUES.setLength(0);
        return vals;
    }

    public void accept(Pair<String, Process> curl) {
        QUEUE.offer(Triple.of(curl.getLeft(), curl.getRight(), System.currentTimeMillis()));
    }

    public Pair<String, InputStream> get() {
        final Triple<String, Process, Long> triple = QUEUE.poll();
        if (triple == null) return null;

        final Process process = triple.getMiddle();
        final Long startTime = triple.getRight();
        final String url = triple.getLeft();

        if (process.isAlive()) {
            if (System.currentTimeMillis() - startTime < timeout) QUEUE.offer(triple);
            else {
                process.destroy();
                EXIT_VALUES.append(". ");
            }
            return null;
        }

        if (process.exitValue() != 0) {
            EXIT_VALUES.append(process.exitValue() + " ");
            return null;
        }

        return Pair.of(url, process.getInputStream());
    }

    public int size() {
        return QUEUE.size();
    }
}
