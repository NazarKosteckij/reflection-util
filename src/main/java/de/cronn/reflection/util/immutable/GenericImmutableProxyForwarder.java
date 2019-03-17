package de.cronn.reflection.util.immutable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.cronn.reflection.util.ClassUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatchers;

public final class GenericImmutableProxyForwarder {

	private static final Logger log = LoggerFactory.getLogger(GenericImmutableProxyForwarder.class);

	private static final Map<Method, Boolean> shouldProxyReturnValueCache = new ConcurrentHashMap<>();

	private GenericImmutableProxyForwarder() {
	}

	@RuntimeType
	public static Object forward(@Origin Method method,
								 @FieldValue(ImmutableProxy.DELEGATE_FIELD_NAME) Object delegate,
								 @AllArguments Object[] args) throws InvocationTargetException, IllegalAccessException {
		Object value = method.invoke(delegate, args);
		if (ImmutableProxy.isImmutable(value)) {
			return value;
		}
		if (!shouldProxyReturnValue(method)) {
			return value;
		}
		if (value instanceof Collection) {
			return createImmutableCollection(value, method);
		} else if (value instanceof Map) {
			return createImmutableMap(value, method);
		} else {
			return ImmutableProxy.create(value);
		}
	}

	@SuppressWarnings("boxing")
	private static boolean shouldProxyReturnValue(Method method) {
		return shouldProxyReturnValueCache.computeIfAbsent(method, m -> {
			if (isCloneMethod(m)) {
				return false;
			}
			ReadOnly readOnlyAnnotation = ClassUtils.findAnnotation(m, ReadOnly.class);
			return readOnlyAnnotation == null
				|| readOnlyAnnotation.proxyReturnValue();
		}).booleanValue();
	}

	private static boolean isCloneMethod(Method method) {
		return ElementMatchers.isClone().matches(new MethodDescription.ForLoadedMethod(method));
	}

	private static Object createImmutableCollection(Object value, Method method) {
		Class<?> returnType = method.getReturnType();
		if (returnType.equals(Set.class)) {
			Set<?> collection = (Set<?>) value;
			return ImmutableProxy.create(collection);
		} else if (returnType.equals(List.class)) {
			List<?> collection = (List<?>) value;
			return ImmutableProxy.create(collection);
		} else if (returnType.equals(Collection.class) || returnType.equals(Iterable.class)) {
			Collection<?> collection = (Collection<?>) value;
			return ImmutableProxy.create(collection);
		} else {
			log.trace("Cannot create immutable collection for {}. Collection type is unknown or too specific: {}", method, returnType);
			return value;
		}
	}

	private static Object createImmutableMap(Object value, Method method) {
		Class<?> returnType = method.getReturnType();
		if (returnType.equals(Map.class)) {
			Map<?, ?> map = (Map<?, ?>) value;
			return ImmutableProxy.create(map);
		} else {
			log.trace("Cannot create immutable map for {}. Map type is unknown or too specific: {}", method, returnType);
			return value;
		}
	}

}