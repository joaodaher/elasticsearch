/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.index.shard;

import org.apache.lucene.index.Term;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.engine.Engine;
import org.elasticsearch.index.engine.InternalEngineTests;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.test.ESTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.is;

public class IndexingOperationListenerTests extends ESTestCase{

    // this test also tests if calls are correct if one or more listeners throw exceptions
    public void testListenersAreExecuted() {
        AtomicInteger preIndex = new AtomicInteger();
        AtomicInteger postIndex = new AtomicInteger();
        AtomicInteger postIndexException = new AtomicInteger();
        AtomicInteger preDelete = new AtomicInteger();
        AtomicInteger postDelete = new AtomicInteger();
        AtomicInteger postDeleteException = new AtomicInteger();
        ShardId randomShardId = new ShardId(new Index(randomAsciiOfLength(10), randomAsciiOfLength(10)), randomIntBetween(1, 10));
        IndexingOperationListener listener = new IndexingOperationListener() {
            @Override
            public Engine.Index preIndex(ShardId shardId, Engine.Index operation) {
                assertThat(shardId, is(randomShardId));
                preIndex.incrementAndGet();
                return operation;
            }

            @Override
            public void postIndex(ShardId shardId, Engine.Index index, Engine.IndexResult result) {
                if (result.hasFailure() == false) {
                    postIndex.incrementAndGet();
                } else {
                    postIndex(shardId, index, result.getFailure());
                }
            }

            @Override
            public void postIndex(ShardId shardId, Engine.Index index, Exception ex) {
                assertThat(shardId, is(randomShardId));
                postIndexException.incrementAndGet();
            }

            @Override
            public Engine.Delete preDelete(ShardId shardId, Engine.Delete delete) {
                assertThat(shardId, is(randomShardId));
                preDelete.incrementAndGet();
                return delete;
            }

            @Override
            public void postDelete(ShardId shardId, Engine.Delete delete, Engine.DeleteResult result) {
                if (result.hasFailure() == false) {
                    postDelete.incrementAndGet();
                } else {
                    postDelete(shardId, delete, result.getFailure());
                }
            }

            @Override
            public void postDelete(ShardId shardId, Engine.Delete delete, Exception ex) {
                assertThat(shardId, is(randomShardId));
                postDeleteException.incrementAndGet();
            }
        };

        IndexingOperationListener throwingListener = new IndexingOperationListener() {
            @Override
            public Engine.Index preIndex(ShardId shardId, Engine.Index index) {
                throw new RuntimeException();
            }

            @Override
            public void postIndex(ShardId shardId, Engine.Index index, Engine.IndexResult result) {
                throw new RuntimeException();
            }

            @Override
            public void postIndex(ShardId shardId, Engine.Index index, Exception ex) {
                throw new RuntimeException();
            }

            @Override
            public void postDelete(ShardId shardId, Engine.Delete delete, Engine.DeleteResult result) {
                throw new RuntimeException();
            }

            @Override
            public Engine.Delete preDelete(ShardId shardId, Engine.Delete delete) {
                throw new RuntimeException();
            }

            @Override
            public void postDelete(ShardId shardId, Engine.Delete delete, Exception ex) {
                throw new RuntimeException();
            }
        };
        final List<IndexingOperationListener> indexingOperationListeners = new ArrayList<>(Arrays.asList(listener, listener));
        if (randomBoolean()) {
            indexingOperationListeners.add(throwingListener);
            if (randomBoolean()) {
                indexingOperationListeners.add(throwingListener);
            }
        }
        Collections.shuffle(indexingOperationListeners, random());
        ParsedDocument doc = InternalEngineTests.createParsedDoc("1", "test", null);
        IndexingOperationListener.CompositeListener compositeListener =
                new IndexingOperationListener.CompositeListener(indexingOperationListeners, logger);
        Engine.Delete delete = new Engine.Delete("test", "1", new Term("_uid", doc.uid()));
        Engine.Index index = new Engine.Index(new Term("_uid", doc.uid()), doc);
        compositeListener.postDelete(randomShardId, delete, new Engine.DeleteResult(0, false));
        assertEquals(0, preIndex.get());
        assertEquals(0, postIndex.get());
        assertEquals(0, postIndexException.get());
        assertEquals(0, preDelete.get());
        assertEquals(2, postDelete.get());
        assertEquals(0, postDeleteException.get());

        compositeListener.postDelete(randomShardId, delete, new RuntimeException());
        assertEquals(0, preIndex.get());
        assertEquals(0, postIndex.get());
        assertEquals(0, postIndexException.get());
        assertEquals(0, preDelete.get());
        assertEquals(2, postDelete.get());
        assertEquals(2, postDeleteException.get());

        compositeListener.preDelete(randomShardId, delete);
        assertEquals(0, preIndex.get());
        assertEquals(0, postIndex.get());
        assertEquals(0, postIndexException.get());
        assertEquals(2, preDelete.get());
        assertEquals(2, postDelete.get());
        assertEquals(2, postDeleteException.get());

        compositeListener.postIndex(randomShardId, index, new Engine.IndexResult(0, false));
        assertEquals(0, preIndex.get());
        assertEquals(2, postIndex.get());
        assertEquals(0, postIndexException.get());
        assertEquals(2, preDelete.get());
        assertEquals(2, postDelete.get());
        assertEquals(2, postDeleteException.get());

        compositeListener.postIndex(randomShardId, index, new RuntimeException());
        assertEquals(0, preIndex.get());
        assertEquals(2, postIndex.get());
        assertEquals(2, postIndexException.get());
        assertEquals(2, preDelete.get());
        assertEquals(2, postDelete.get());
        assertEquals(2, postDeleteException.get());

        compositeListener.preIndex(randomShardId, index);
        assertEquals(2, preIndex.get());
        assertEquals(2, postIndex.get());
        assertEquals(2, postIndexException.get());
        assertEquals(2, preDelete.get());
        assertEquals(2, postDelete.get());
        assertEquals(2, postDeleteException.get());
    }
}
