package com.localdev.studiox.core.engine;

/**
 * Explicit service lifecycle states matching XAMPP Control Panel requirements.
 */
public enum ServiceState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}
