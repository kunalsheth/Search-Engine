package urls;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.*;
import java.nio.file.NotDirectoryException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

public class UrlStore implements Supplier<String>, Consumer<String> {

    private static File dir;

    private static UrlStore ourInstance = new UrlStore();
    private static DB database;
    private static HTreeMap<String, Long> map;
    private static long reindexTime;

    private static final Object READ_LOCK = new Object(), WRITE_LOCK = new Object();

    private static File postQueue;
    private static File preQueue;
    private static BufferedReader reader;
    private static PrintWriter writer;
    private static Map<String, String> env = new HashMap<>();
    private static File scriptFile;
    private static String script;

    public static void init(final Stream<String> seed, final File dir, final long allocatedSize, final long allocateIncrement, final long reindexTime) throws IOException {
        if (dir.exists() && !dir.isDirectory()) throw new NotDirectoryException(dir.getAbsolutePath());
        else if (!dir.exists()) dir.mkdirs();
        UrlStore.dir = dir;

        postQueue = new File(dir + "/post.q");
        preQueue = new File(dir + "/pre.q");
        postQueue.createNewFile();
        preQueue.createNewFile();
        writer = new PrintWriter(new FileWriter(preQueue));
        reader = new BufferedReader(new FileReader(postQueue));

        script = "shuf ${pre} > ${post}\n" +
                "> ${pre}";

        scriptFile = new File(dir + "/cycle.sh");
        OutputStream os = new FileOutputStream(scriptFile);
        os.write(script.getBytes());
        os.flush();
        os.close();
        env.put("pre", preQueue.getName());
        env.put("post", postQueue.getName());

        UrlStore.reindexTime = reindexTime;

        database = DBMaker
                .fileDB(dir + "/map.db")
                .allocateStartSize(allocatedSize)
                .allocateIncrement(allocateIncrement)
                .fileMmapEnable()
                .fileMmapPreclearDisable()
                .make();

        map = database.hashMap("map")
                .layout(8, 16, 7)
                .keySerializer(Serializer.STRING_ASCII)
                .valueSerializer(Serializer.LONG)
                .createOrOpen();

        seed.forEach(getInstance());
    }

    private static synchronized void cycle() {
        try {
            synchronized (READ_LOCK) {
                reader.lines().forEach(writer::println);
                reader.close();

                synchronized (WRITE_LOCK) {
                    writer.flush();
                    writer.close();

                    ProcessBuilder pb = new ProcessBuilder()
                            .command("/bin/bash", scriptFile.getName())
                            .directory(dir)
                            .inheritIO();
                    pb.environment().putAll(env);
                    pb.start().waitFor();

                    writer = new PrintWriter(new FileWriter(preQueue));
                }
                reader = new BufferedReader(new FileReader(postQueue));
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static UrlStore getInstance() {
        return ourInstance;
    }

    public void accept(final String s) {
        final int octothorpeIndex = s.lastIndexOf('#');
        final int questionMarkIndex = s.lastIndexOf('?');

        if (octothorpeIndex != -1 || questionMarkIndex != -1) {
            if (octothorpeIndex != -1 && questionMarkIndex == -1)
                accept(s.substring(0, octothorpeIndex));
            else if (octothorpeIndex == -1 && questionMarkIndex != -1)
                accept(s.substring(0, questionMarkIndex));
            else if (octothorpeIndex != -1 && questionMarkIndex != -1)
                accept(s.substring(0, Math.min(octothorpeIndex, questionMarkIndex)));

            return;
        }

        final String key = s.replaceFirst("(https|http|ftp)://", "");
        final Long time = map.get(key);

        if (time == null) {
            map.put(key, System.currentTimeMillis());
            synchronized (WRITE_LOCK) {
                writer.println(s);
            }
        } else if (System.currentTimeMillis() - time > reindexTime) {
            map.replace(key, System.currentTimeMillis());
            synchronized (WRITE_LOCK) {
                writer.println(s);
            }
        }
    }

    public String get() {
        final String url;
        try {
            synchronized (READ_LOCK) {
                url = reader.readLine();
                if (url == null) cycle();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return url;
    }
}