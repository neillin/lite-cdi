/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ethwt.core.cdi.example;

import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.slf4j.Logger;

import com.ethwt.core.cdi.annotation.ConfigValue;

@ApplicationScoped
public class Generator {
	
	@Inject
	Logger log;

    @Inject
    @ConfigValue(defaultValue = "10000")
    int numberOfGeneratedStrings;

    @Inject
    @ConfigValue(defaultValue = "0")
    long sleepFor;

    @Inject
    @Generated
    Instance<String> instance;

    @Inject
    @Generated
    Event<String> event;

    public void run() {
        long startTime = System.nanoTime();
        for (int i = 0; i < numberOfGeneratedStrings; i++) {
            String produced = instance.get();
            event.fire(produced);
            // Destroy the produced instance - it's a dependent object of the Generator
            instance.destroy(produced);
        }
        log.info(numberOfGeneratedStrings + " strings generated in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + " ms");

        // Just for profiling
        if (sleepFor > 0) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(sleepFor));
            } catch (InterruptedException e) {
                throw new IllegalStateException();
            }
        }
    }

    @PostConstruct
    void init() {
        System.out.println("I am ready to generate...");
    }

    @PreDestroy
    void destroy() {
        System.out.println("This is the end...");
    }

}
