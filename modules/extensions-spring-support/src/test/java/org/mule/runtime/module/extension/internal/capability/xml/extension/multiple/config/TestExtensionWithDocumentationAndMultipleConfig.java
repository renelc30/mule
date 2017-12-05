/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.capability.xml.extension.multiple.config;

import org.mule.runtime.api.meta.Category;
import org.mule.runtime.extension.api.annotation.Alias;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.runtime.extension.api.annotation.metadata.MetadataScope;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.metadata.NullMetadataResolver;

/**
 * Test Extension Description
 */
@Extension(name = "multiple", category = Category.PREMIUM)
@MetadataScope(outputResolver = NullMetadataResolver.class)
@Alias("some-alias")
@Configurations({TestDocumentedConfig.class, TestAnotherDocumentedConfig.class})
@Xml(namespace = "namespaceLocation", prefix = "documentation")
public class TestExtensionWithDocumentationAndMultipleConfig {

  @Parameter
  String param;

}
