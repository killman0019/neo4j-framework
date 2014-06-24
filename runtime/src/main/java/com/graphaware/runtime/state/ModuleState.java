package com.graphaware.runtime.state;

import com.graphaware.runtime.config.TxDrivenModuleConfiguration;

/**
 * An interface for objects representing a {@link com.graphaware.runtime.module.RuntimeModule}'s state.
 */
public interface ModuleState {

    /**
     * Get the last configuration with which the module was run.
     *
     * @return last configuration.
     */
    TxDrivenModuleConfiguration getConfiguration();
}
