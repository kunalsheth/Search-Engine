package index;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.common.SolrInputDocument;
import utils.Sleeper;

import java.io.IOException;
import java.util.function.Consumer;

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

public class SolrManager implements Consumer<SolrInputDocument> {
    private static SolrManager ourInstance = new SolrManager();

    private static SolrClient solrClient;

    private static long lastCommitTime;

    private SolrManager() {
    }

    public static void init(final String url, final int queueSize, final int threadCount, final long commitTime) {
        solrClient = new ConcurrentUpdateSolrClient
                .Builder(url)
                .withQueueSize(queueSize)
                .withThreadCount(threadCount)
                .build();

        new Thread(() -> {
            while (true) {
                Sleeper.sleep(commitTime);
                commit();
            }
        }).start();

        Runtime.getRuntime().addShutdownHook(new Thread(SolrManager::commit));
    }

    private static void commit() {
        lastCommitTime = System.currentTimeMillis();
        try {
            solrClient.commit(true, false);
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SolrManager getInstance() {
        return ourInstance;
    }


    public void accept(SolrInputDocument document) {
        try {
            solrClient.add(document);
        } catch (SolrServerException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public long getLastCommitTime() {
        return lastCommitTime;
    }
}
