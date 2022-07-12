/**
 * 
 */
package com.ethwt.core.cdi.example;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * @author neillin
 *
 */
class GeneratorTest {
	
   @BeforeAll
   public static void setup() {
	   Arc.initialize();
   }
    @Test
    public void testSelect() {
    	ArcContainer container = Arc.container();
        assertTrue(container.select(BeanManager.class).isResolvable());
        BeanManager beanMgr = CDI.current().getBeanManager();
        assertNotNull(beanMgr);
        Generator generator = container.instance(Generator.class).get();
        assertNotNull(generator);
        generator.run();
    }

}
