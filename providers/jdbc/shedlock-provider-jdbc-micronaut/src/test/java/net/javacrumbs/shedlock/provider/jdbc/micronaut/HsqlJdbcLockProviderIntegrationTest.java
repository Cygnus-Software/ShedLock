/**
 * Copyright 2009-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jdbc.micronaut;

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.HsqlConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class HsqlJdbcLockProviderIntegrationTest extends AbstractJdbcLockProviderIntegrationTest {
    private static final HsqlConfig dbConfig = new HsqlConfig();

    @BeforeAll
    public static void startDb() {
        dbConfig.startDb();
    }

    @AfterAll
    public static void shutDownDb() {
        dbConfig.shutdownDb();
    }

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }

    @Override
    protected boolean useDbTime() {
        return false;
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new MicronautJdbcLockProvider(testUtils.getDatasource());
    }
}
