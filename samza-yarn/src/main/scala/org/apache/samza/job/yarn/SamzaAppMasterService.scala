/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.samza.job.yarn

import org.apache.samza.util.Logging
import org.apache.samza.config.Config
import org.apache.samza.metrics.ReadableMetricsRegistry
import org.apache.samza.SamzaException
import org.apache.samza.coordinator.server.HttpServer
import org.apache.samza.coordinator.server.JobServlet
import org.apache.samza.webapp.ApplicationMasterRestServlet
import org.apache.samza.webapp.ApplicationMasterWebServlet

/**
 * Samza's application master runs a very basic HTTP/JSON service to allow
 * dashboards to check on the status of a job. SamzaAppMasterService starts
 * up the web service when initialized.
 */
class SamzaAppMasterService(config: Config, state: SamzaAppMasterState, registry: ReadableMetricsRegistry, clientHelper: ClientHelper) extends YarnAppMasterListener with Logging {
  var rpcApp: HttpServer = null
  var webApp: HttpServer = null
  var coordinatorApp: HttpServer = null

  override def onInit() {
    // try starting the samza AM dashboard at a random rpc and tracking port
    info("Starting webapp at a random rpc and tracking port")

    rpcApp = new HttpServer(resourceBasePath = "scalate")
    rpcApp.addServlet("/*", new ApplicationMasterRestServlet(config, state, registry))
    rpcApp.start

    webApp = new HttpServer(resourceBasePath = "scalate")
    webApp.addServlet("/*", new ApplicationMasterWebServlet(config, state))
    webApp.start

    coordinatorApp = new HttpServer
    coordinatorApp.addServlet("/*", new JobServlet(config, state.tasksToSSPTaskNames, state.taskNameToChangeLogPartitionMapping))
    coordinatorApp.start

    state.rpcUrl = rpcApp.getUrl
    state.trackingUrl = webApp.getUrl
    state.coordinatorUrl = coordinatorApp.getUrl

    info("Webapp is started at (rpc %s, tracking %s, coordinator %s)" format (state.rpcUrl, state.trackingUrl, state.coordinatorUrl))
  }

  override def onShutdown() {
    if (rpcApp != null) {
      rpcApp.stop
    }

    if (webApp != null) {
      webApp.stop
    }

    if (coordinatorApp != null) {
      coordinatorApp.stop
    }
  }
}
