/*
 *  Copyright (c) 2026 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus IT Group - initial API and implementation
 *
 */

/**
 * Control-plane extension that loads the shared category whitelist from a ConfigMap-backed file
 * at boot time and holds it in memory.
 */
package org.eclipse.edc.dse.controlplane.category.whitelist;
