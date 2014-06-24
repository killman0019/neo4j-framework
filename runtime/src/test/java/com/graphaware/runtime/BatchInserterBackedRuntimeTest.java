/*
 * Copyright (c) 2013 GraphAware
 *
 * This file is part of GraphAware.
 *
 * GraphAware is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.graphaware.runtime;

import com.graphaware.common.serialize.Serializer;
import com.graphaware.common.strategy.InclusionStrategies;
import com.graphaware.runtime.config.DefaultRuntimeConfiguration;
import com.graphaware.runtime.config.MinimalTxDrivenModuleConfiguration;
import com.graphaware.runtime.config.NullTxDrivenModuleConfiguration;
import com.graphaware.runtime.strategy.BatchSupportingTransactionDrivenRuntimeModule;
import com.graphaware.tx.event.batch.api.TransactionSimulatingBatchInserter;
import com.graphaware.tx.event.batch.api.TransactionSimulatingBatchInserterImpl;
import com.graphaware.tx.event.improved.api.ImprovedTransactionData;
import com.graphaware.tx.executor.single.SimpleTransactionExecutor;
import com.graphaware.tx.executor.single.VoidReturningCallback;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.tooling.GlobalGraphOperations;
import org.neo4j.unsafe.batchinsert.BatchInserters;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import static com.graphaware.common.util.IterableUtils.count;
import static com.graphaware.runtime.GraphAwareRuntimeFactory.*;
import static com.graphaware.runtime.config.RuntimeConfiguration.GA_PREFIX;
import static com.graphaware.runtime.config.RuntimeConfiguration.GA_METADATA;
import static com.graphaware.runtime.manager.BaseModuleManager.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link BatchInserterBackedRuntime}.
 */
public class BatchInserterBackedRuntimeTest extends GraphAwareRuntimeTest {

    private final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        temporaryFolder.create();
    }

    @After
    public void tearDown() {
        temporaryFolder.delete();
    }

    @Test
    public void shouldCreateRuntimeRootNodeAfterFirstStartup() {
        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.start();
        batchInserter.shutdown();

        final GraphDatabaseService database = new GraphDatabaseFactory().newEmbeddedDatabase(temporaryFolder.getRoot().getAbsolutePath());

        new SimpleTransactionExecutor(database).executeInTransaction(new VoidReturningCallback() {
            @Override
            protected void doInTx(GraphDatabaseService database) {
                assertEquals(1, Iterables.count(GlobalGraphOperations.at(database).getAllNodesWithLabel(GA_METADATA)));
            }
        });

        database.shutdown();
    }

    @Test
    public void moduleRegisteredForTheFirstTimeShouldBeInitialized() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start();

        verify(mockModule).initialize(batchInserter);
        verify(mockModule, atLeastOnce()).getConfiguration();
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(Serializer.toString(NullTxDrivenModuleConfiguration.getInstance(), CONFIG), batchInserter.getNodeProperties(0).get(GA_PREFIX + RUNTIME + "_" + MOCK).toString());
    }

    @Test
    public void moduleAlreadyRegisteredShouldNotBeInitialized() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        long root = batchInserter.createNode(Collections.<String, Object>singletonMap(GA_PREFIX + RUNTIME + "_" + MOCK, Serializer.toString(NullTxDrivenModuleConfiguration.getInstance(), CONFIG)), GA_METADATA);

        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start();

        verify(mockModule, atLeastOnce()).getId();
        verify(mockModule, atLeastOnce()).getConfiguration();
        verifyNoMoreInteractions(mockModule);

        assertEquals(Serializer.toString(NullTxDrivenModuleConfiguration.getInstance(), CONFIG), batchInserter.getNodeProperties(root).get(GA_PREFIX + RUNTIME + "_" + MOCK).toString());
    }

    private TransactionSimulatingBatchInserter createBatchInserter() {
        return new TransactionSimulatingBatchInserterImpl(BatchInserters.inserter(temporaryFolder.getRoot().getAbsolutePath()));
    }

    @Test
    public void changedModuleShouldBeReInitialized() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        long root = batchInserter.createNode(Collections.<String, Object>singletonMap(GA_PREFIX + RUNTIME + "_" + MOCK, CONFIG + "123"), GA_METADATA);

        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start();

        verify(mockModule).reinitialize(batchInserter);
        verify(mockModule, atLeastOnce()).getConfiguration();
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(Serializer.toString(NullTxDrivenModuleConfiguration.getInstance(), CONFIG), batchInserter.getNodeProperties(root).get(GA_PREFIX + RUNTIME + "_" + MOCK).toString());
    }

    @Test
    public void forcedModuleShouldBeReInitialized() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        long root = batchInserter.createNode(new HashMap<String, Object>(), GA_METADATA);
        batchInserter.setNodeProperty(root, GA_PREFIX + RUNTIME + "_" + MOCK, FORCE_INITIALIZATION + "123");

        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start();

        verify(mockModule).reinitialize(batchInserter);
        verify(mockModule, atLeastOnce()).getConfiguration();
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(Serializer.toString(NullTxDrivenModuleConfiguration.getInstance(), CONFIG), batchInserter.getNodeProperties(root).get(GA_PREFIX + RUNTIME + "_" + MOCK).toString());
    }

    @Test
    public void changedModuleShouldNotBeReInitializedWhenInitializationSkipped() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        long root = batchInserter.createNode(Collections.<String, Object>singletonMap(GA_PREFIX + RUNTIME + "_" + MOCK, CONFIG + "123"), GA_METADATA);

        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start(true);

        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);

        assertEquals(CONFIG + "123", batchInserter.getNodeProperties(root).get(GA_PREFIX + RUNTIME + "_" + MOCK).toString());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotBeAbleToRegisterTheSameModuleTwice() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);
        runtime.registerModule(mockModule);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldNotBeAbleToRegisterModuleWithTheSameIdTwice() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule1 = createBatchSupportingMockModule();
        final BatchSupportingTransactionDrivenRuntimeModule mockModule2 = createBatchSupportingMockModule();
        when(mockModule1.getId()).thenReturn("ID");
        when(mockModule2.getId()).thenReturn("ID");

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule1);
        runtime.registerModule(mockModule2);
    }

    @Test
    public void unusedModulesShouldBeRemoved() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        long root = batchInserter.createNode(Collections.<String, Object>emptyMap(), GA_METADATA);
        batchInserter.setNodeProperty(root, GA_PREFIX + RUNTIME + "_" + MOCK, Serializer.toString(NullTxDrivenModuleConfiguration.getInstance(), CONFIG));
        batchInserter.setNodeProperty(root, GA_PREFIX + RUNTIME + "_UNUSED", CONFIG + "123");

        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start();

        verify(mockModule, atLeastOnce()).getId();
        verify(mockModule, atLeastOnce()).getConfiguration();
        verifyNoMoreInteractions(mockModule);

        assertEquals(1, count(batchInserter.getNodeProperties(root).keySet()));
    }

    @Test(expected = IllegalStateException.class)
    public void usedCorruptModulesShouldThrowException() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        batchInserter.createNode(Collections.<String, Object>singletonMap(GA_PREFIX + RUNTIME + "_" + MOCK, "CORRUPT"), GA_METADATA);

        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start();
    }

    @Test
    public void unusedCorruptModulesShouldBeRemoved() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        long root = batchInserter.createNode(Collections.<String, Object>emptyMap(), GA_METADATA);
        batchInserter.setNodeProperty(root, GA_PREFIX + RUNTIME + "_" + MOCK, Serializer.toString(NullTxDrivenModuleConfiguration.getInstance(), CONFIG));
        batchInserter.setNodeProperty(root, GA_PREFIX + RUNTIME + "_UNUSED", "CORRUPT");

        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start();

        verify(mockModule, atLeastOnce()).getId();
        verify(mockModule, atLeastOnce()).getConfiguration();
        verifyNoMoreInteractions(mockModule);

        assertEquals(1, count(batchInserter.getNodeProperties(root).keySet()));
    }

    @Test
    public void allRegisteredInterestedModulesShouldBeDelegatedTo() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule1 = createBatchSupportingMockModule();
        when(mockModule1.getId()).thenReturn("MOCK1");
        when(mockModule1.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());

        final BatchSupportingTransactionDrivenRuntimeModule mockModule2 = createBatchSupportingMockModule();
        when(mockModule2.getId()).thenReturn("MOCK2");
        when(mockModule2.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());

        final BatchSupportingTransactionDrivenRuntimeModule mockModule3 = createBatchSupportingMockModule();
        when(mockModule3.getId()).thenReturn("MOCK3");
        when(mockModule3.getConfiguration()).thenReturn(new MinimalTxDrivenModuleConfiguration(InclusionStrategies.none()));

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule1);
        runtime.registerModule(mockModule2);
        runtime.registerModule(mockModule3);

        runtime.start();

        verify(mockModule1).initialize(batchInserter);
        verify(mockModule2).initialize(batchInserter);
        verify(mockModule3).initialize(batchInserter);
        verify(mockModule1, atLeastOnce()).getConfiguration();
        verify(mockModule2, atLeastOnce()).getConfiguration();
        verify(mockModule3, atLeastOnce()).getConfiguration();
        verify(mockModule1, atLeastOnce()).getId();
        verify(mockModule2, atLeastOnce()).getId();
        verify(mockModule3, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule1, mockModule2, mockModule3);

        batchInserter.createNode(null);
        batchInserter.shutdown();

        verify(mockModule1).beforeCommit(any(ImprovedTransactionData.class));
        verify(mockModule2).beforeCommit(any(ImprovedTransactionData.class));
        verify(mockModule1, atLeastOnce()).getConfiguration();
        verify(mockModule2, atLeastOnce()).getConfiguration();
        verify(mockModule3, atLeastOnce()).getConfiguration();
        verify(mockModule1).shutdown();
        verify(mockModule2).shutdown();
        verify(mockModule3).shutdown();

        //no interaction with module3, it is not interested!
        verifyNoMoreInteractions(mockModule1, mockModule2, mockModule3);
    }

    @Test
    public void moduleThrowingInitExceptionShouldBeMarkedForReinitialization() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();
        when(mockModule.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());
        Mockito.doThrow(new NeedsInitializationException()).when(mockModule).beforeCommit(any(ImprovedTransactionData.class));

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start();

        assertTrue(batchInserter.getNodeProperties(0).get(GA_PREFIX + RUNTIME + "_" + MOCK).toString().startsWith(CONFIG));

        batchInserter.createNode(null);
        batchInserter.shutdown();

        batchInserter = new TransactionSimulatingBatchInserterImpl(createBatchInserter());
        assertTrue(batchInserter.getNodeProperties(0).get(GA_PREFIX + RUNTIME + "_" + MOCK).toString().startsWith(FORCE_INITIALIZATION));
    }

    @Test
    public void moduleThrowingInitExceptionShouldBeMarkedForReinitializationOnlyTheFirstTime() throws InterruptedException {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();
        when(mockModule.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());
        doThrow(new NeedsInitializationException()).when(mockModule).beforeCommit(any(ImprovedTransactionData.class));

        TransactionSimulatingBatchInserter batchInserter = new TransactionSimulatingBatchInserterImpl(BatchInserters.inserter(temporaryFolder.getRoot().getAbsolutePath()), 0);
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        runtime.start();

        batchInserter.createNode(null);

        long firstFailureTimestamp = Long.valueOf(batchInserter.getNodeProperties(0).get(GA_PREFIX + RUNTIME + "_" + MOCK).toString().replaceFirst(FORCE_INITIALIZATION, ""));

        Thread.sleep(1);

        batchInserter.createNode(null);

        long secondFailureTimestamp = Long.valueOf(batchInserter.getNodeProperties(0).get(GA_PREFIX + RUNTIME + "_" + MOCK).toString().replaceFirst(FORCE_INITIALIZATION, ""));

        batchInserter.shutdown();

        assertEquals(firstFailureTimestamp, secondFailureTimestamp);
    }

    @Test(expected = IllegalStateException.class)
    public void modulesCannotBeRegisteredAfterStart() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.start(true);
        runtime.registerModule(mockModule);
    }

    @Test
    public void multipleCallsToStartFrameworkHaveNoEffect() {
        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.start();
        runtime.start();
        runtime.start();
        runtime.start();
    }

    @Test
    public void runtimeConfiguredModulesShouldBeConfigured() {
        RuntimeConfiguredRuntimeModule mockModule = mock(RuntimeConfiguredRuntimeModule.class);
        when(mockModule.getId()).thenReturn(MOCK);
        when(mockModule.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);

        verify(mockModule).configurationChanged(DefaultRuntimeConfiguration.getInstance());
        verify(mockModule, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule);
    }

    @Test
    public void realRuntimeConfiguredModulesShouldBeConfigured() {
        RealRuntimeConfiguredRuntimeModule module = new RealRuntimeConfiguredRuntimeModule();

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(module);

        assertEquals(DefaultRuntimeConfiguration.getInstance(), module.getConfig());
    }

    @Test(expected = IllegalStateException.class)
    public void unConfiguredModuleShouldThrowException() {
        RealRuntimeConfiguredRuntimeModule module = new RealRuntimeConfiguredRuntimeModule();
        module.getConfig();
    }

    @Test
    public void shutdownShouldBeCalledBeforeShutdown() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule = createBatchSupportingMockModule();
        when(mockModule.getId()).thenReturn(MOCK);
        when(mockModule.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule);
        runtime.start();

        batchInserter.shutdown();

        verify(mockModule).shutdown();
    }

    @Test
    public void whenOneModuleThrowsAnExceptionThenOtherModulesShouldStillBeDelegatedTo() {
        final BatchSupportingTransactionDrivenRuntimeModule mockModule1 = createBatchSupportingMockModule();
        when(mockModule1.getId()).thenReturn(MOCK + "1");
        when(mockModule1.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());
        doThrow(new RuntimeException()).when(mockModule1).beforeCommit(any(ImprovedTransactionData.class));

        final BatchSupportingTransactionDrivenRuntimeModule mockModule2 = createBatchSupportingMockModule();
        when(mockModule2.getId()).thenReturn(MOCK + "2");
        when(mockModule2.getConfiguration()).thenReturn(NullTxDrivenModuleConfiguration.getInstance());

        TransactionSimulatingBatchInserter batchInserter = createBatchInserter();
        GraphAwareRuntime runtime = createRuntime(batchInserter);
        runtime.registerModule(mockModule1);
        runtime.registerModule(mockModule2);

        runtime.start();

        verify(mockModule1).initialize(batchInserter);
        verify(mockModule2).initialize(batchInserter);
        verify(mockModule1, atLeastOnce()).getConfiguration();
        verify(mockModule2, atLeastOnce()).getConfiguration();
        verify(mockModule1, atLeastOnce()).getId();
        verify(mockModule2, atLeastOnce()).getId();
        verifyNoMoreInteractions(mockModule1, mockModule2);

        batchInserter.createNode(new HashMap<String, Object>());
        batchInserter.shutdown();

        verify(mockModule1).beforeCommit(any(ImprovedTransactionData.class));
        verify(mockModule2).beforeCommit(any(ImprovedTransactionData.class));
    }
}
