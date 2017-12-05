/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.resources;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.mule.runtime.api.dsl.DslResolvingContext.getDefault;
import static org.mule.runtime.core.api.util.ClassUtils.withContextClassLoader;
import static org.mule.runtime.core.api.util.ExceptionUtils.extractOfType;
import static org.mule.runtime.module.extension.api.loader.AbstractJavaExtensionModelLoader.TYPE_PROPERTY_NAME;
import static org.mule.runtime.module.extension.api.loader.AbstractJavaExtensionModelLoader.VERSION;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.registry.SpiServiceRegistry;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.dsl.syntax.resources.spi.DslResourceFactory;
import org.mule.runtime.extension.api.exception.IllegalModelDefinitionException;
import org.mule.runtime.extension.api.loader.ExtensionModelLoader;
import org.mule.runtime.extension.api.persistence.ExtensionModelJsonSerializer;
import org.mule.runtime.extension.api.resources.ResourcesGenerator;
import org.mule.runtime.extension.api.resources.spi.GeneratedResourceFactory;
import org.mule.runtime.module.extension.internal.capability.xml.schema.ExtensionAnnotationProcessor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;


/**
 * Annotation processor that picks up all the extensions annotated with {@link Extension} and use a
 * {@link ResourcesGenerator} to generated the required resources.
 * <p>
 * This annotation processor will automatically generate and package into the output jar the XSD schema, spring bundles and
 * extension registration files necessary for mule to work with this extension.
 * <p>
 * Depending on the model properties declared by each extension, some of those resources might or might not be generated
 *
 * @since 3.7.0
 */
@SupportedAnnotationTypes(value = {"org.mule.runtime.extension.api.annotation.Extension"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(BaseExtensionResourcesGeneratorAnnotationProcessor.EXTENSION_VERSION)
public abstract class BaseExtensionResourcesGeneratorAnnotationProcessor extends AbstractProcessor {

  private static final ExtensionAnnotationProcessor processor = new ExtensionAnnotationProcessor();

  public static final String PROCESSING_ENVIRONMENT = "PROCESSING_ENVIRONMENT";
  public static final String EXTENSION_ELEMENT = "EXTENSION_ELEMENT";
  public static final String ROUND_ENVIRONMENT = "ROUND_ENVIRONMENT";
  public static final String EXTENSION_VERSION = "extension.version";

  private final SpiServiceRegistry serviceRegistry = new SpiServiceRegistry();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    log("Starting Resources generator for Extensions");
    ResourcesGenerator generator = new AnnotationProcessorResourceGenerator(fetchResourceFactories(), processingEnv);

    try {
      getExtension(roundEnv).ifPresent(extensionElement -> {
        Optional<Class<Object>> annotatedClass = processor.classFor(extensionElement, processingEnv);
        if (!annotatedClass.isPresent()) {
          log("Extension class " + processor.getClassName(extensionElement, processingEnv) + " could not be found. Skipping");
          return;
        }
        final Class<?> extensionClass = annotatedClass.get();
        withContextClassLoader(extensionClass.getClassLoader(), () -> {
          ExtensionModel extensionModel = parseExtension(extensionElement, roundEnv);
          ExtensionModelJsonSerializer jsonSerializer = new ExtensionModelJsonSerializer(true);
          String serialize = jsonSerializer.serialize(extensionModel);
          File file = new File("/Users/estebanwasinger/Desktop/", extensionModel.getName() + ".json");
          try {
            file.createNewFile();
            Files.write(serialize.getBytes(), file);
          } catch (IOException e) {
            e.printStackTrace();
          }

          generator.generateFor(extensionModel);
        });
      });


      return false;
    } catch (MuleRuntimeException e) {
      Optional<IllegalModelDefinitionException> exception = extractOfType(e, IllegalModelDefinitionException.class);
      if (exception.isPresent()) {
        throw exception.get();
      }
      processingEnv.getMessager().printMessage(ERROR, format("%s\n%s", e.getMessage(), getStackTrace(e)));
      throw e;
    }
  }

  private ExtensionModel parseExtension(TypeElement extensionElement, RoundEnvironment roundEnvironment) {
    Class<?> extensionClass = processor.classFor(extensionElement, processingEnv).get();

    Map<String, Object> params = new HashMap<>();
    params.put(TYPE_PROPERTY_NAME, extensionClass.getName());
    params.put(VERSION, getVersion(extensionElement.getQualifiedName()));
    params.put(EXTENSION_ELEMENT, extensionElement);
    params.put(PROCESSING_ENVIRONMENT, processingEnv);
    params.put(ROUND_ENVIRONMENT, roundEnvironment);
    params.put("EXTENSION_ELEMENT", extensionElement);
    return getExtensionModelLoader().loadExtensionModel(extensionClass.getClassLoader(), getDefault(emptySet()), params);
  }

  private Optional<TypeElement> getExtension(RoundEnvironment env) {
    Set<TypeElement> elements = processor.getTypeElementsAnnotatedWith(Extension.class, env);
    //    TypeElement next = elements.iterator().next();
    //    ExtensionTypeElement extensionTypeElement = new ExtensionTypeElement(next, processingEnv, env);
    //    VariableElement enumValue = extensionTypeElement.getValueFromAnnotation(Extension.class).getEnumValue(Extension::category);
    ////    List<FieldElement> fields = extensionTypeElement.getFields();
    ////    FieldElement fieldElement = fields.get(0);
    //
    //    Category category = extensionTypeElement.getCategory();
    //
    //    ConfigurationElement configurationElement = extensionTypeElement.getConfigurations().get(0);
    //    List<OperationContainerElement> operationContainers =
    //        configurationElement.getOperationContainers();
    ////
    //    Optional<MetadataScope> annotation = configurationElement.getAnnotation(MetadataScope.class);
    //    configurationElement.getAnnotationValue(MetadataScope.class, MetadataScope::outputResolver);
    ////
    //    ASTUtils.ASTValueFetcher<OutputResolver> annotation2 = operationContainers.get(0).getOperations().get(0).getValueFromAnnotation(OutputResolver.class);
    //    Type classValue = annotation2.getClassValue(OutputResolver::attributes);
    //    List<MethodElement> operations = operationContainers.get(0).getOperations();
    //    ExtensionParameter extensionParameter = operations.get(0).getParameters().get(0);
    //    Type type = extensionParameter.getType();
    //    java.lang.reflect.Type javaType = extensionParameter.getJavaType();
    //

    if (elements.size() > 1) {
      String message =
          format("Only one extension is allowed per plugin, however several classes annotated with @%s were found. Offending classes are [%s]",
                 Extension.class.getSimpleName(),
                 Joiner.on(", ").join(elements.stream().map(TypeElement::getQualifiedName).collect(toList())));

      throw new RuntimeException(message);
    }

    Optional<TypeElement> first = elements.stream().findFirst();

    //    ExtensionTypeElement extensionTypeElement = new ExtensionTypeElement(first.get(), processingEnv, env);
    //    List<MethodElement> operations = extensionTypeElement.getConfigurations().get(0).getOperationContainers().get(0).getOperations();
    //    MethodElement methodElement = operations.get(0);
    //
    //    Type returnTypeElemet = methodElement.getReturnTypeElement();

    return first;
  }

  private void log(String message) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
  }

  private String getVersion(Name qualifiedName) {
    String extensionVersion = processingEnv.getOptions().get(EXTENSION_VERSION);
    if (extensionVersion == null) {
      throw new RuntimeException(String.format("Cannot resolve version for extension %s: option '%s' is missing.", qualifiedName,
                                               EXTENSION_VERSION));
    }

    return extensionVersion;
  }

  private List<GeneratedResourceFactory> fetchResourceFactories() {

    return ImmutableList.<GeneratedResourceFactory>builder()
        .addAll(serviceRegistry.lookupProviders(GeneratedResourceFactory.class, getClass().getClassLoader()))
        .addAll(serviceRegistry.lookupProviders(DslResourceFactory.class, getClass().getClassLoader())).build();

  }

  protected abstract ExtensionModelLoader getExtensionModelLoader();
}
