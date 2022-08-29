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

package com.ethwt.core.cdi.tool;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

import io.quarkus.arc.processor.AlternativePriorities;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.arc.processor.BeanArchives;
import io.quarkus.arc.processor.BeanDefiningAnnotation;
import io.quarkus.arc.processor.BeanDeploymentValidator;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BeanProcessor;
import io.quarkus.arc.processor.BeanRegistrar;
import io.quarkus.arc.processor.ContextRegistrar;
import io.quarkus.arc.processor.InjectionPointsTransformer;
import io.quarkus.arc.processor.InterceptorBindingRegistrar;
import io.quarkus.arc.processor.ObserverRegistrar;
import io.quarkus.arc.processor.ObserverTransformer;
import io.quarkus.arc.processor.QualifierRegistrar;
import io.quarkus.arc.processor.ResourceOutput;

/**
 *
 * @author Martin Kouba
 */
@Mojo(name = "process", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BeanMojo extends AbstractMojo {

    @Parameter(readonly = true, required = true, defaultValue = "${project.build.directory}/generated-sources")
    private File targetDirectory;

    @Parameter(readonly = true, required = true, defaultValue = "${project.build.outputDirectory}")
    private File outputDirectory;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter
    private String[] additionalBeanDefiningAnnotations;

    @Parameter
    private String[] excludeDependencies;


    private final List<Class<? extends Annotation>> resourceAnnotations;


    private final boolean removeUnusedBeans;
    private final List<Predicate<BeanInfo>> exclusions;

    private final AlternativePriorities alternativePriorities;

    public BeanMojo() {
        this.resourceAnnotations = Collections.emptyList();

        this.removeUnusedBeans = false;
        this.exclusions = Collections.emptyList();
        this.alternativePriorities = null;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        IndexView beanArchiveIndex;
        try {
        	beanArchiveIndex = createArchiveIndex();
        } catch (IOException e) {
            throw new MojoFailureException("Failed to create bean archive index", e);
        }
        
        IndexView applicationIndex;
        try {
        	applicationIndex = createApplicationIndex();
        } catch (IOException e) {
            throw new MojoFailureException("Failed to create application index", e);
        }

        BeanProcessor.Builder builder = BeanProcessor.builder()
                .setBeanArchiveIndex(BeanArchives.buildBeanArchiveIndex(getClass().getClassLoader(),
                        new ConcurrentHashMap<>(), beanArchiveIndex))
                .setApplicationIndex(applicationIndex);
        if (!resourceAnnotations.isEmpty()) {
            builder.addResourceAnnotations(resourceAnnotations.stream()
                    .map(c -> DotName.createSimple(c.getName()))
                    .collect(Collectors.toList()));
        }
        getBeanRegistrars().forEach(builder::addBeanRegistrar);
        getObserverRegistrars().forEach(builder::addObserverRegistrar);
        getContextRegistrars().forEach(builder::addContextRegistrar);
        getQualifierRegistrars().forEach(builder::addQualifierRegistrar);
        getInterceptorBindingRegistrars().forEach(builder::addInterceptorBindingRegistrar);
        getAnnotationsTransformers().forEach(builder::addAnnotationTransformer);
        getInjectionPointsTransformers().forEach(builder::addInjectionPointTransformer);
        getObserverTransformers().forEach(builder::addObserverTransformer);
        getBeanDeploymentValidators().forEach(builder::addBeanDeploymentValidator);
        builder.setAdditionalBeanDefiningAnnotations(
        		getAdditionalBeanDefiningAnnotations().stream().
        		map((s) -> new BeanDefiningAnnotation(s, null)).
        		collect(Collectors.toSet())
        );
        builder.setOutput(new ResourceOutput() {

        	@Override
	        public void writeResource(Resource resource) throws IOException {
	        switch (resource.getType()) {
	            case JAVA_CLASS:
	                resource.writeTo(outputDirectory);
	                break;
	            case SERVICE_PROVIDER:
	                resource.writeTo(new File(outputDirectory, "/META-INF/services/"));
	            default:
	                break;
	        }
      }

        });
        builder.setRemoveUnusedBeans(removeUnusedBeans);
        for (Predicate<BeanInfo> exclusion : exclusions) {
            builder.addRemovalExclusion(exclusion);
        }
        builder.setAlternativePriorities(alternativePriorities);

        BeanProcessor beanProcessor = builder.build();

        try {
            beanProcessor.process();
        } catch (Exception e) {
            throw new MojoExecutionException("Error generating resources", e);
        }
    }
    
    private boolean isDependencyToScan(Artifact artifact) {
    	if (excludeDependencies == null || excludeDependencies.length == 0) {
    		return true;
    	}
        String id = artifact.getGroupId() + ":" + artifact.getArtifactId();
        for (String dependency : excludeDependencies) {
            if (dependency.equals(id)) {
                return false;
            }
        }
        return true;
    }


    private IndexView createArchiveIndex() throws IOException {

        // Index dependencies
        List<IndexView> depIndexes = new ArrayList<>();
        List<File> dependenciesToIndex = project.getArtifacts().stream().
        		filter(this::isDependencyToScan).
        		map(Artifact::getFile).
        		filter(IndexingUtil::isBeanArchive).
                collect(Collectors.toList());
        for (File dependency : dependenciesToIndex) {
        	getLog().info("Add jar file: "+dependency.getName()+" to bean archive.");
            depIndexes.add(IndexingUtil.indexJar(dependency));
        }

        // Index output directory, i.e. target/classes
        Indexer indexer = new Indexer();
        Files.walkFileTree(outputDirectory.toPath(), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".class")) {
                    try (InputStream stream = Files.newInputStream(file)) {
                        indexer.index(stream);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        depIndexes.add(indexer.complete());
        return CompositeIndex.create(depIndexes);
    }

    private Index createApplicationIndex() throws IOException {
        Indexer indexer = new Indexer();

        return indexer.complete();
    }

   

    private Collection<DotName> getAdditionalBeanDefiningAnnotations() {
        List<DotName> beanDefiningAnnotations = Collections.emptyList();
        if (additionalBeanDefiningAnnotations != null && additionalBeanDefiningAnnotations.length > 0) {
            beanDefiningAnnotations = new ArrayList<>();
            for (String beanDefiningAnnotation : additionalBeanDefiningAnnotations) {
                beanDefiningAnnotations.add(DotName.createSimple(beanDefiningAnnotation));
            }
        }
        return beanDefiningAnnotations;
    }

	public ServiceLoader<BeanRegistrar> getBeanRegistrars() {
		return ServiceLoader.load(BeanRegistrar.class, getClass().getClassLoader());
	}

	public ServiceLoader<ObserverRegistrar> getObserverRegistrars() {
		return ServiceLoader.load(ObserverRegistrar.class, getClass().getClassLoader());
	}

	public ServiceLoader<ContextRegistrar> getContextRegistrars() {
		return ServiceLoader.load(ContextRegistrar.class, getClass().getClassLoader());
	}

	public ServiceLoader<InterceptorBindingRegistrar> getInterceptorBindingRegistrars() {
		return ServiceLoader.load(InterceptorBindingRegistrar.class, getClass().getClassLoader());
	}

	public ServiceLoader<AnnotationsTransformer> getAnnotationsTransformers() {
		return ServiceLoader.load(AnnotationsTransformer.class, getClass().getClassLoader());
	}

	public ServiceLoader<InjectionPointsTransformer> getInjectionPointsTransformers() {
		return ServiceLoader.load(InjectionPointsTransformer.class, getClass().getClassLoader());
	}

	public ServiceLoader<BeanDeploymentValidator> getBeanDeploymentValidators() {
		return ServiceLoader.load(BeanDeploymentValidator.class, getClass().getClassLoader());
	}

	public ServiceLoader<QualifierRegistrar> getQualifierRegistrars() {
		return ServiceLoader.load(QualifierRegistrar.class, getClass().getClassLoader());
	}

	public ServiceLoader<ObserverTransformer> getObserverTransformers() {
		return ServiceLoader.load(ObserverTransformer.class, getClass().getClassLoader());
	}

}