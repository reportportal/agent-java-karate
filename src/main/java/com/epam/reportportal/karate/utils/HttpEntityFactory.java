/*
 * Copyright 2026 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.karate.utils;

import com.epam.reportportal.formatting.http.HttpFormatUtils;
import com.epam.reportportal.formatting.http.HttpPartFormatter;
import com.epam.reportportal.formatting.http.HttpRequestFormatter;
import com.epam.reportportal.formatting.http.HttpResponseFormatter;
import com.epam.reportportal.formatting.http.entities.BodyType;
import com.epam.reportportal.formatting.http.entities.Cookie;
import com.epam.reportportal.formatting.http.entities.Header;
import com.epam.reportportal.formatting.http.entities.Param;
import io.karatelabs.http.HttpRequest;
import io.karatelabs.http.HttpResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Utility factory methods that convert Karate HTTP entities into ReportPortal formatters.
 */
public final class HttpEntityFactory {

	private HttpEntityFactory() {
		throw new IllegalStateException("Utility class should not be instantiated");
	}

	/**
	 * Builds a request formatter from Karate {@link HttpRequest}.
	 *
	 * @param request             Karate HTTP request
	 * @param uriConverter        URI converter override
	 * @param headerConverter     header converter override
	 * @param cookieConverter     cookie converter override
	 * @param paramConverter      form parameter converter override
	 * @param prettifiers         payload prettifiers by MIME type
	 * @param partHeaderConverter multipart part header converter override
	 * @param bodyTypeMap         body type mapping by MIME type
	 * @return populated request formatter
	 */
	@Nonnull
	public static HttpRequestFormatter createHttpRequestFormatter(@Nonnull HttpRequest request,
			@Nullable Function<String, String> uriConverter, @Nullable Function<Header, String> headerConverter,
			@Nullable Function<Cookie, String> cookieConverter, @Nullable Function<Param, String> paramConverter,
			@Nullable Map<String, Function<String, String>> prettifiers, @Nullable Function<Header, String> partHeaderConverter,
			@Nonnull Map<String, BodyType> bodyTypeMap) {
		HttpRequestFormatter.Builder builder = new HttpRequestFormatter.Builder(request.getMethod(), request.getUrlAndPath());
		ofNullable(request.getHeaders()).ifPresent(headers -> headers.forEach((key, values) -> ofNullable(values).orElseGet(Collections::emptyList)
				.forEach(value -> builder.addHeader(key, value))));
		ofNullable(request.getCookies()).orElseGet(Collections::emptyMap)
				.forEach((key, value) -> builder.addCookie(key, value == null ? null : value.get("value")));
		builder.uriConverter(uriConverter)
				.headerConverter(headerConverter)
				.cookieConverter(cookieConverter)
				.paramConverter(paramConverter)
				.prettifiers(prettifiers);

		String contentType = request.getContentType();
		String mimeType = HttpFormatUtils.getMimeType(contentType);
		BodyType bodyType = request.isMultiPart() ? BodyType.MULTIPART : HttpFormatUtils.getBodyType(contentType, bodyTypeMap);
		byte[] body = request.getBody();
		if (body == null && !BodyType.MULTIPART.equals(bodyType)) {
			return builder.build();
		}
		switch (bodyType) {
			case TEXT -> builder.bodyText(mimeType, ofNullable(request.getBodyDisplay()).orElseGet(request::getBodyString));
			case FORM -> builder.bodyParams(ofNullable(request.getBodyDisplay()).orElseGet(request::getBodyString));
			case MULTIPART -> toParts(request, bodyTypeMap, partHeaderConverter).forEach(builder::addBodyPart);
			default -> builder.bodyBytes(mimeType, body);
		}
		return builder.build();
	}

	/**
	 * Builds a response formatter from Karate {@link HttpResponse}.
	 *
	 * @param response        Karate HTTP response
	 * @param headerConverter header converter override
	 * @param cookieConverter cookie converter override
	 * @param prettifiers     payload prettifiers by MIME type
	 * @param bodyTypeMap     body type mapping by MIME type
	 * @return populated response formatter
	 */
	@Nonnull
	public static HttpResponseFormatter createHttpResponseFormatter(@Nonnull HttpResponse response,
			@Nullable Function<Header, String> headerConverter, @Nullable Function<Cookie, String> cookieConverter,
			@Nullable Map<String, Function<String, String>> prettifiers, @Nonnull Map<String, BodyType> bodyTypeMap) {
		HttpResponseFormatter.Builder builder = new HttpResponseFormatter.Builder(response.getStatus(), getStatusLine(response));
		ofNullable(response.getHeaders()).ifPresent(headers -> headers.forEach((key, values) -> ofNullable(values).orElseGet(Collections::emptyList)
				.forEach(value -> builder.addHeader(key, value))));
		ofNullable(response.getCookies()).orElseGet(Collections::emptyMap).forEach((name, cookie) -> builder.addCookie(
				name,
				toStringValue(cookie.get("value")),
				toStringValue(cookie.get("comment")),
				toStringValue(cookie.get("path")),
				toStringValue(cookie.get("domain")),
				toLongValue(cookie.get("maxAge")),
				toBooleanValue(cookie.get("secure")),
				toBooleanValue(cookie.get("httpOnly")),
				toInstantValue(cookie.get("expires")),
				toIntegerValue(cookie.get("version")),
				toStringValue(cookie.get("sameSite"))
		));
		builder.headerConverter(headerConverter).cookieConverter(cookieConverter).prettifiers(prettifiers);
		Object body = response.getBody();
		if (body == null) {
			return builder.build();
		}
		String contentType = response.getContentType();
		String mimeType = HttpFormatUtils.getMimeType(contentType);
		BodyType bodyType = HttpFormatUtils.getBodyType(contentType, bodyTypeMap);
		if (BodyType.TEXT == bodyType) {
			builder.bodyText(mimeType, response.getBodyString());
		} else {
			builder.bodyBytes(mimeType, response.getBodyBytes());
		}
		return builder.build();
	}

	@Nonnull
	private static List<HttpPartFormatter> toParts(@Nonnull HttpRequest request, @Nonnull Map<String, BodyType> bodyTypeMap,
			@Nullable Function<Header, String> partHeaderConverter) {
		Map<String, List<Map<String, Object>>> multipartMap = request.getMultiParts();
		if (multipartMap == null || multipartMap.isEmpty()) {
			return Collections.emptyList();
		}
		List<HttpPartFormatter> parts = new ArrayList<>();
		multipartMap.values().stream().filter(Objects::nonNull).flatMap(List::stream).forEach(part -> {
			String partMimeType = ofNullable(part.get("contentType")).map(Object::toString).orElse("application/octet-stream");
			Object value = part.get("value");
			BodyType partBodyType = HttpFormatUtils.getBodyType(partMimeType, bodyTypeMap);
			HttpPartFormatter.PartType partType =
					BodyType.TEXT == partBodyType ? HttpPartFormatter.PartType.TEXT : HttpPartFormatter.PartType.BINARY;
			Object payload = partType == HttpPartFormatter.PartType.TEXT ? toTextValue(value) : toBinaryValue(value);
			HttpPartFormatter.Builder partBuilder = new HttpPartFormatter.Builder(partType, partMimeType, payload);
			partBuilder.controlName(ofNullable(part.get("name")).map(Object::toString).orElse(null));
			partBuilder.charset(ofNullable(part.get("charset")).map(Object::toString).orElse(null));
			partBuilder.fileName(ofNullable(part.get("filename")).map(Object::toString).orElse(null));
			partBuilder.headerConverter(partHeaderConverter);
			parts.add(partBuilder.build());
		});
		return parts;
	}

	@Nonnull
	private static String getStatusLine(@Nonnull HttpResponse response) {
		return isNotBlank(response.getStatusText()) ?
				response.getStatus() + " " + response.getStatusText() :
				Integer.toString(response.getStatus());
	}

	@Nonnull
	private static String toTextValue(@Nullable Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof byte[] bytes) {
			return new String(bytes, StandardCharsets.UTF_8);
		}
		return value.toString();
	}

	@Nonnull
	private static byte[] toBinaryValue(@Nullable Object value) {
		if (value == null) {
			return new byte[0];
		}
		if (value instanceof byte[] bytes) {
			return bytes;
		}
		return value.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Nullable
	private static String toStringValue(@Nullable Object value) {
		return value == null ? null : value.toString();
	}

	@Nullable
	private static Long toLongValue(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		try {
			return Long.parseLong(value.toString());
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	@Nullable
	private static Integer toIntegerValue(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return Integer.parseInt(value.toString());
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	@Nullable
	private static Boolean toBooleanValue(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean b) {
			return b;
		}
		return Boolean.parseBoolean(value.toString());
	}

	@Nullable
	private static Instant toInstantValue(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Instant instant) {
			return instant;
		}
		try {
			return Instant.parse(value.toString());
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}
}
