/*
 * Copyright 2017 HugeGraph Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.baidu.hugegraph.unit.cache;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.baidu.hugegraph.backend.cache.RamCache;
import com.baidu.hugegraph.backend.id.Id;
import com.baidu.hugegraph.backend.id.IdGenerator;
import com.baidu.hugegraph.testutil.Assert;
import com.baidu.hugegraph.testutil.Whitebox;
import com.baidu.hugegraph.unit.BaseUnitTest;
import com.baidu.hugegraph.util.Bytes;

public class RamCacheTest extends BaseUnitTest {

    @Before
    public void setup() {
        // pass
    }

    @After
    public void teardown() throws Exception {
        // pass
    }

    @Test
    public void testUpdateAndGet() {
        RamCache cache = new RamCache();
        Id id = IdGenerator.of("1");
        Assert.assertNull(cache.get(id));

        cache.update(id, "value-1");
        Assert.assertEquals("value-1", cache.get(id));

        cache.update(id, "value-2");
        Assert.assertEquals("value-2", cache.get(id));
    }

    @Test
    public void testUpdateAndGetWithSizeEqualCapacity() {
        RamCache cache = new RamCache(4);
        cache.update(IdGenerator.of("1"), "value-1");
        cache.update(IdGenerator.of("2"), "value-2");
        cache.update(IdGenerator.of("3"), "value-3");
        cache.update(IdGenerator.of("4"), "value-4");

        Assert.assertEquals("value-1", cache.get(IdGenerator.of("1")));
        Assert.assertEquals("value-2", cache.get(IdGenerator.of("2")));
        Assert.assertEquals("value-3", cache.get(IdGenerator.of("3")));
        Assert.assertEquals("value-4", cache.get(IdGenerator.of("4")));
    }

    @Test
    public void testGetOrFetch() {
        RamCache cache = new RamCache();
        Id id = IdGenerator.of("1");
        Assert.assertNull(cache.get(id));

        Assert.assertEquals("value-1",  cache.getOrFetch(id, key -> {
            return "value-1";
        }));

        cache.update(id, "value-2");
        Assert.assertEquals("value-2",  cache.getOrFetch(id, key -> {
            return "value-1";
        }));
    }

    @Test
    public void testUpdateIfAbsent() {
        RamCache cache = new RamCache();
        Id id = IdGenerator.of("1");
        cache.updateIfAbsent(id, "value-1");
        Assert.assertEquals("value-1", cache.get(id));
    }

    @Test
    public void testUpdateIfAbsentWithExistKey() {
        RamCache cache = new RamCache();
        Id id = IdGenerator.of("1");
        cache.update(id, "value-1");
        cache.updateIfAbsent(id, "value-2");
        Assert.assertEquals("value-1", cache.get(id));
    }

    @Test
    public void testUpdateIfPresent() {
        RamCache cache = new RamCache();
        Id id = IdGenerator.of("1");
        cache.updateIfPresent(id, "value-1");
        Assert.assertEquals(null, cache.get(id));

        cache.update(id, "value-1");
        Assert.assertEquals("value-1", cache.get(id));
        cache.updateIfPresent(id, "value-2");
        Assert.assertEquals("value-2", cache.get(id));
    }

    @Test
    public void testInvalidate() {
        RamCache cache = new RamCache();
        Id id = IdGenerator.of("1");
        cache.update(id, "value-1");
        Assert.assertEquals("value-1", cache.get(id));
        Assert.assertEquals(1L, cache.size());

        Object queue = Whitebox.getInternalState(cache, "queue");
        Assert.assertThrows(RuntimeException.class, () -> {
            Whitebox.invoke(queue.getClass(), new Class[]{Object.class},
                            "checkNotInQueue", queue, id);
        });

        cache.invalidate(id);
        Assert.assertEquals(null, cache.get(id));
        Assert.assertEquals(0L, cache.size());

        Assert.assertTrue(Whitebox.invoke(queue.getClass(),
                                          new Class[]{Object.class},
                                          "checkNotInQueue", queue, id));
    }

    @Test
    public void testClear() {
        RamCache cache = new RamCache();
        Id id = IdGenerator.of("1");
        cache.update(id, "value-1");
        Assert.assertEquals("value-1", cache.get(id));
        id = IdGenerator.of("2");
        cache.update(id, "value-2");
        cache.clear();
        Assert.assertEquals(null, cache.get(id));
        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void testCapacity() {
        RamCache cache = new RamCache(10);
        Assert.assertEquals(10, cache.capacity());

        cache = new RamCache(1024);
        Assert.assertEquals(1024, cache.capacity());

        cache = new RamCache(8);
        Assert.assertEquals(8, cache.capacity());

        cache = new RamCache(1);
        Assert.assertEquals(1, cache.capacity());

        cache = new RamCache(0);
        Assert.assertEquals(0, cache.capacity());

        int huge = (int) (200 * Bytes.GB);
        cache = new RamCache(huge);
        Assert.assertEquals(huge, cache.capacity());

        // The min capacity is 0
        cache = new RamCache(-1);
        Assert.assertEquals(0, cache.capacity());
    }

    @Test
    public void testSize() {
        RamCache cache = new RamCache();
        Id id = IdGenerator.of("1");
        cache.update(id, "value-1");
        id = IdGenerator.of("2");
        cache.update(id, "value-2");
        Assert.assertEquals(2, cache.size());
    }

    @Test
    public void testSizeWithReachCapacity() {
        RamCache cache = new RamCache(10);
        for (int i = 0; i < 20; i++) {
            Id id = IdGenerator.of("key-" + i);
            cache.update(id, "value-" + i);
        }
        Assert.assertEquals(10, cache.size());
    }

    @Test
    public void testHitsAndMiss() {
        RamCache cache = new RamCache();
        Assert.assertEquals(0L, cache.hits());
        Assert.assertEquals(0L, cache.miss());

        Id id = IdGenerator.of("1");
        cache.update(id, "value-1");
        Assert.assertEquals(0L, cache.hits());
        Assert.assertEquals(0L, cache.miss());

        cache.get(IdGenerator.of("not-exist"));
        Assert.assertEquals(0L, cache.hits());
        Assert.assertEquals(1L, cache.miss());

        cache.get(IdGenerator.of("1"));
        Assert.assertEquals(1L, cache.hits());
        Assert.assertEquals(1L, cache.miss());

        cache.get(IdGenerator.of("not-exist"));
        Assert.assertEquals(1L, cache.hits());
        Assert.assertEquals(2L, cache.miss());

        cache.get(IdGenerator.of("1"));
        Assert.assertEquals(2L, cache.hits());
        Assert.assertEquals(2L, cache.miss());
    }

    @Test
    public void testExpire() {
        RamCache cache = new RamCache();
        cache.update(IdGenerator.of("1"), "value-1");
        cache.update(IdGenerator.of("2"), "value-2");

        Assert.assertEquals(2, cache.size());
        Assert.assertEquals(0L, cache.expire());

        cache.expire(2); // 2 seconds
        Assert.assertEquals(2000L, cache.expire());
        waitTillNext(2);
        cache.tick();

        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void testExpireWithAddNewItem() {
        RamCache cache = new RamCache();
        cache.update(IdGenerator.of("1"), "value-1");
        cache.update(IdGenerator.of("2"), "value-2");

        Assert.assertEquals(2, cache.size());

        cache.expire(2);

        waitTillNext(1);
        cache.tick();

        cache.update(IdGenerator.of("3"), "value-3");
        cache.tick();

        Assert.assertEquals(3, cache.size());

        waitTillNext(1);
        cache.tick();

        Assert.assertEquals(1, cache.size());
        Assert.assertNotNull(cache.get(IdGenerator.of("3")));

        waitTillNext(1);
        cache.tick();

        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void testExpireWithZeroSecond() {
        RamCache cache = new RamCache();
        cache.update(IdGenerator.of("1"), "value-1");
        cache.update(IdGenerator.of("2"), "value-2");

        Assert.assertEquals(2, cache.size());

        cache.expire(0);
        waitTillNext(1);
        cache.tick();

        Assert.assertEquals(2, cache.size());
    }

    private static final int THREADS_NUM = 8;

    @Test
    public void testMutiThreadsUpdate() {
        RamCache cache = new RamCache(THREADS_NUM * 10000 * 10);

        runWithThreads(THREADS_NUM, () -> {
            for (int i = 0; i < 10000 * 10; i++) {
                Id id = IdGenerator.of(
                        Thread.currentThread().getName() + "-" + i);
                cache.update(id, "value-" + i);
            }
        });
        Assert.assertEquals(THREADS_NUM * 10000 * 10, cache.size());
    }

    @Test
    public void testMutiThreadsUpdateWithGtCapacity() {
        RamCache cache = new RamCache(10);

        runWithThreads(THREADS_NUM, () -> {
            for (int i = 0; i < 10000 * 100; i++) {
                Id id = IdGenerator.of(
                        Thread.currentThread().getName() + "-" + i);
                cache.update(id, "value-" + i);
            }
        });
        Assert.assertEquals(10, cache.size());
    }

    @Test
    public void testMutiThreadsUpdateAndCheck() {
        RamCache cache = new RamCache();

        runWithThreads(THREADS_NUM, () -> {
            Map<Id, Object> all = new HashMap<>(1000);

            for (int i = 0; i < 1000; i++) {
                Id id = IdGenerator.of(Thread.currentThread().getName() +
                                       "-" + i);
                String value = "value-" + i;
                cache.update(id, value);

                all.put(id, value);
            }

            @SuppressWarnings("unchecked")
            Map<Id, ?> map = (Map<Id, ?>) Whitebox.getInternalState(cache,
                                                                    "map");
            Object queue = Whitebox.getInternalState(cache, "queue");
            for (Map.Entry<Id, Object> entry : all.entrySet()) {
                Id key = entry.getKey();
                Assert.assertEquals(entry.getValue(), cache.get(key));
                Assert.assertTrue(Whitebox.invoke(queue.getClass(),
                                                  "checkPrevNotInNext",
                                                  queue, map.get(key)));
            }
        });
        Assert.assertEquals(THREADS_NUM * 1000, cache.size());
    }

    @Test
    public void testMutiThreadsGetWith2Items() {
        RamCache cache = new RamCache(10);

        Id id1 = IdGenerator.of("1");
        cache.update(id1, "value-1");
        Id id2 = IdGenerator.of("2");
        cache.update(id2, "value-2");
        Assert.assertEquals(2, cache.size());

        runWithThreads(THREADS_NUM, () -> {
            for (int i = 0; i < 10000 * 100; i++) {
                Assert.assertEquals("value-1", cache.get(id1));
                Assert.assertEquals("value-2", cache.get(id2));
                Assert.assertEquals("value-1", cache.get(id1));
            }
        });
        Assert.assertEquals(2, cache.size());
    }

    @Test
    public void testMutiThreadsGetWith3Items() {
        RamCache cache = new RamCache(10);

        Id id1 = IdGenerator.of("1");
        cache.update(id1, "value-1");
        Id id2 = IdGenerator.of("2");
        cache.update(id2, "value-2");
        Id id3 = IdGenerator.of("3");
        cache.update(id3, "value-3");
        Assert.assertEquals(3, cache.size());

        runWithThreads(THREADS_NUM, () -> {
            for (int i = 0; i < 10000 * 100; i++) {
                Assert.assertEquals("value-1", cache.get(id1));
                Assert.assertEquals("value-2", cache.get(id2));
                Assert.assertEquals("value-2", cache.get(id2));
                Assert.assertEquals("value-3", cache.get(id3));
                Assert.assertEquals("value-1", cache.get(id1));
            }
        });
        Assert.assertEquals(3, cache.size());
    }

    @Test
    public void testMutiThreadsGetAndUpdate() {
        RamCache cache = new RamCache(10);

        Id id1 = IdGenerator.of("1");
        cache.update(id1, "value-1");
        Id id2 = IdGenerator.of("2");
        cache.update(id2, "value-2");
        Id id3 = IdGenerator.of("3");
        cache.update(id3, "value-3");
        Assert.assertEquals(3, cache.size());

        runWithThreads(THREADS_NUM, () -> {
            for (int i = 0; i < 10000 * 100; i++) {
                Assert.assertEquals("value-1", cache.get(id1));
                Assert.assertEquals("value-2", cache.get(id2));
                Assert.assertEquals("value-2", cache.get(id2));
                Assert.assertEquals("value-3", cache.get(id3));
                cache.update(id2, "value-2");
                Assert.assertEquals("value-1", cache.get(id1));
            }
        });
        Assert.assertEquals(3, cache.size());
    }

    @Test
    public void testMutiThreadsGetAndUpdateWithGtCapacity() {
        RamCache cache = new RamCache(10);

        runWithThreads(THREADS_NUM, () -> {
            for (int i = 0; i < 10000 * 20; i++) {
                for (int k = 0; k < 15; k++) {
                    Id id = IdGenerator.of(k);
                    Object value = cache.get(id);
                    if (value != null) {
                        Assert.assertEquals("value-" + k, value);
                    } else {
                        cache.update(id, "value-" + k);
                    }
                }
            }
        });
        // In fact, the size may be any value(such as 43)
        Assert.assertTrue(cache.size() < 10 + THREADS_NUM);
    }
}

