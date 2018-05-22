<!--
# Copyright (C) 2017 Create-Net / FBK.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License 2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-2.0/
# 
# SPDX-License-Identifier: EPL-2.0
# 
# Contributors:
#     Create-Net / FBK - initial API and implementation
-->

Agile Bluetooth Low Energy components
===

This repository contains the alpha version of Agile Bluetooth Low Energy protocol implementation:


Exposes the [org.eclipse.agail.Protocol](http://agile-iot.github.io/agile-api-spec/docs/html/api.html#iot_agile_Protocol) interface

- DBus interface name **org.eclipse.agail.protocol.BLE**
- DBus interface path **/org/eclipse/agail/protocol/BLE**

Launching the Protocol
---

Under the `scripts` directory you can find different scripts to setup and start / stop the modules.

*Note* A `java` 8 compatible JDK and maven (`mvn`) must be already available in the system.

- `./scripts/start.sh` will install (if not available) all the dependencies required to run and start the module.

- `./scripts/stop.sh` will stop/kill the modules.

-  `./scripts/install-deps.sh` is used to setup all the java and native dependencies to a local directory (placed in the `./deps` folder)
