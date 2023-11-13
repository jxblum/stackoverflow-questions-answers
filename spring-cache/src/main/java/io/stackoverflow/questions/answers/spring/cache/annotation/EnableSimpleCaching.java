/*
 * Copyright 2020-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.stackoverflow.questions.answers.spring.cache.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.annotation.Import;

/**
 * Spring Java {@link Annotation} used to enable a simple cache provider implementation using {@link ConcurrentMap}.
 *
 * @author John Blum
 * @see java.lang.annotation.Annotation
 * @see io.stackoverflow.questions.answers.spring.cache.annotation.SimpleCachingConfiguration
 * @see org.springframework.context.annotation.Import
 * @since 1.0.0
 */
@Target({ ElementType.ANNOTATION_TYPE, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Import(SimpleCachingConfiguration.class)
@SuppressWarnings("unused")
public @interface EnableSimpleCaching {

}
