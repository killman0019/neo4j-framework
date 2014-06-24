package com.graphaware.runtime.manager;

import com.graphaware.runtime.metadata.ModuleMetadataRepository;
import com.graphaware.runtime.module.TxDrivenModule;
import org.apache.log4j.Logger;
import org.neo4j.graphdb.GraphDatabaseService;

/**
 *
 */
public class ProductionTransactionDrivenModuleManager extends BaseTransactionDrivenModuleManager<TxDrivenModule>  {
    private static final Logger LOG = Logger.getLogger(ProductionTransactionDrivenModuleManager.class);

    private final GraphDatabaseService database;

    public ProductionTransactionDrivenModuleManager(ModuleMetadataRepository metadataRepository, GraphDatabaseService database) {
        super(metadataRepository);
        this.database = database;
    }

    @Override
    protected void doInitialize(TxDrivenModule module) {
        module.initialize(database);
    }

    @Override
    protected void doReinitialize(TxDrivenModule module) {
        module.reinitialize(database);
    }
}
