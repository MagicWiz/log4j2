/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.config;

/**
 * Interface that must be implemented to provide notification of configuration changes.
 */
public interface ConfigurationMonitor {

    /**
     * Called to determine if the configuration has changed.
     */
    void checkConfiguration();

    /**
     * Determines how to log events reliably during or after a configuration change.
     * 
     * @param loggerConfig the LoggerConfig the resulting {@code ReliabilityStrategy} is associated with
     * @return a ReliabilityStrategy that helps the specified LoggerConfig to log events reliably during or after a
     *         configuration change
     */
    ReliabilityStrategy getReliabilityStrategy(final LoggerConfig loggerConfig);
}
