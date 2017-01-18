import index.IndexManager;
import index.SolrManager;
import org.apache.commons.lang3.tuple.Pair;
import urls.CurlStore;
import urls.UrlStore;
import utils.Sleeper;

import java.io.*;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

public class Main {

    private static UrlStore urlStore;
    private static SolrManager solrManager;
    private static CurlStore curlStore;
    private static Runtime runtime;
    private static Thread.UncaughtExceptionHandler handler;
    private static int indexCounter = 0;

    public static void main(String[] args) throws IOException {
        final PrintWriter error;
        try {6
            error = new PrintWriter(new FileWriter("errlog.txt"));
            System.out.println("Initializing URL Store");
            UrlStore.init(getSeed(), new File("store"), 4_000_000_000L, 1_000_000_000, TimeUnit.DAYS.toMillis(7));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        urlStore = UrlStore.getInstance();

        System.out.println("Initializing Solr Manager");
        SolrManager.init("http://localhost:2357/solr/www", 1000, 2, TimeUnit.MINUTES.toMillis(3));
        solrManager = SolrManager.getInstance();

        System.out.println("Initializing CURL Store");
        curlStore = CurlStore.getInstance(250, TimeUnit.SECONDS.toMillis(30));

        System.out.println("Initializing Runtime");
        runtime = Runtime.getRuntime();

        System.out.println("Initializing UncaughtExceptionHandler");
        handler = (t, e) -> {
            synchronized (error) {
                error.println("Thread: " + t.getName());
                error.println(e.getMessage());
                e.printStackTrace(error);

                error.println();
                error.println();
                error.println();

                error.flush();
            }
        };

        final Thread urlThread = new Thread(() -> {
            Stream.generate(urlStore)
                    .filter(s -> s != null)
                    .map(url -> Pair.of(url, curlStore.curl(url)))
                    .filter(pair -> pair != null && pair.getRight() != null)
                    .forEach(curlStore);
        });
        urlThread.setPriority(Thread.MAX_PRIORITY);
        urlThread.setName("URL Thread");
        urlThread.setUncaughtExceptionHandler(handler);
        urlThread.start();

        final Thread indexThread = new Thread(() ->
                Stream.generate(curlStore).parallel()
                        .filter(pair -> pair != null)
                        .map(pair -> IndexManager.parse(pair.getLeft(), pair.getRight()))
                        .filter(document -> document != null)
                        .map(IndexManager::index)
                        .forEach(triple -> {
                            solrManager.accept(triple.getLeft());
                            triple.getMiddle().forEach(urlStore);
                            incrementIndexCounter();
                        })
        );
        indexThread.setPriority(Thread.MAX_PRIORITY);
        indexThread.setName("Index Thread");
        indexThread.setUncaughtExceptionHandler(handler);
        indexThread.start();

        while (true) {
            final int usedMemory = (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1000000);
            final int curlsRunning = curlStore.size();
            final Date lastSolrCommit = Date.from(Instant.ofEpochMilli(solrManager.getLastCommitTime()));
            final String exitVals = curlStore.getExitValues();
            final int indexesPerSecond = indexCounter;
            indexCounter = 0;

            System.out.print("\nUsed Memory: " + usedMemory + " MB\n" +
                    "Curls Running: " + curlsRunning + "\n" +
                    "Curl Exit Values: " + exitVals + "\n" +
                    "Indexes Per Second: " + indexesPerSecond + "\n" +
                    "Last Solr Commit: " + lastSolrCommit + "\n");

            Sleeper.sleep(1000);
        }
    }

    private static void incrementIndexCounter() {
        indexCounter++;
    }

    private static Stream<String> getSeed() {
        BufferedReader seedReader = null;
        try {
            seedReader = new BufferedReader(new FileReader("seed.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (seedReader != null) return seedReader.lines();
        else return null;
    }
}
