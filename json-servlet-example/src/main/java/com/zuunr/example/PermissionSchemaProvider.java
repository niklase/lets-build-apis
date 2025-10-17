package com.zuunr.example;

import com.zuunr.json.*;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PermissionSchemaProvider {

    private static final Logger logger = LoggerFactory.getLogger(PermissionSchemaProvider.class);
    private static final String BASEDIR = "/access-control";
    private final JsonObjectFactory factory = new JsonObjectFactory();
    private Map<String, List<PathPatternPermissionConfig>> permissionConfByMethodAndPathPattern = new HashMap<>();
    private JsonObject permissionSchemas = JsonObject.EMPTY;

    private final ResourcePatternResolver resourcePatternResolver;

    public PermissionSchemaProvider(@Autowired ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
        readAndCacheSchemas();
    }

    private boolean validSchema(final JsonObject jsonObject) {
        if (jsonObject == null) {
            return false;
        }

        final JsonValue jsonValue = jsonObject.get("valid");
        if (jsonValue == null) {
            return false;
        }
        return Boolean.TRUE.equals(jsonValue.getBoolean());
    }

    public JsonValue getRequestPermissionSchema(String path, String method, String permissionName) {
        String permConfKey = permissionConfKeyOf(method, "request", permissionName);
        return getPermissionSchema(path, permConfKey);
    }
    public JsonValue getResponsePermissionSchema(String path, String method, String permissionName) {
        String permConfKey = permissionConfKeyOf(method, "response", permissionName);
        return getPermissionSchema(path, permConfKey);
    }

    public JsonValue getPermissionSchema(String path, String permConfKey) {


        List<PathPatternPermissionConfig> list = permissionConfByMethodAndPathPattern.get(permConfKey);

        PathPatternPermissionConfig pathPatternPermissionConfig = findBestMatch(list, path);
        if (pathPatternPermissionConfig == null) {
            return JsonValue.FALSE;
        } else {
            return pathPatternPermissionConfig.permissionConfig.jsonValue();
        }
    }

    String permissionConfKeyOf(String method, String requestOrResponse, String permissionName) {
        return method.toUpperCase() + "-" + requestOrResponse + "-" + permissionName;
    }

    public static PathPatternPermissionConfig findBestMatch(List<PathPatternPermissionConfig> pathPatternPermissionConfigs, String requestUri) {
        PathPatternPermissionConfig bestMatch = null;

        PathContainer pathContainer = PathContainer.parsePath(requestUri);
        for (PathPatternPermissionConfig pathPatternPermissionConfig : pathPatternPermissionConfigs) {
            if (pathPatternPermissionConfig.pathPattern.matches(pathContainer)) {

                if (bestMatch == null) {
                    bestMatch = pathPatternPermissionConfig;
                } else {
                    int comparision = pathPatternPermissionConfig.pathPattern.compareTo(bestMatch.pathPattern);
                    if (comparision == 0) {
                        throw new RuntimeException("These paths matches the same request URIs: " + pathPatternPermissionConfig.pathPattern.toString() + ", " + bestMatch.pathPattern);
                    }
                    if (comparision < 1) {
                        bestMatch = pathPatternPermissionConfig;
                    }
                }
            }
        }
        return bestMatch;
    }

    private void readAndCacheSchemas() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(
                    ResourcePatternResolver.CLASSPATH_URL_PREFIX + BASEDIR + "/**/*/*.schema.json");
            Resource baseDirResource = resourcePatternResolver.getResource(
                    ResourcePatternResolver.CLASSPATH_URL_PREFIX + BASEDIR);

            // Get a normalized base URI (handle JAR-based resources)
            String basePath = normalizeResourcePath(baseDirResource);

            Arrays.stream(resources)
                    .forEach(resource -> {
                        String urlPathAndMethodAndSchema;

                        try {
                            String resourcePath = normalizeResourcePath(resource);
                            if (resourcePath != null) {
                                // Remove basePath prefix from resourcePath
                                urlPathAndMethodAndSchema = resourcePath.replace(basePath, "");

                                Pattern pattern = Pattern.compile(
                                        "^(.*)/([^/]*)/([^/]*)[.](request|response)[.]schema[.]json$");

                                Matcher matcher = pattern.matcher(urlPathAndMethodAndSchema);
                                if (matcher.find()) {
                                    String urlPath = matcher.group(1);
                                    String method = matcher.group(2);
                                    String permissionName = matcher.group(3);
                                    String requestOrResponse = matcher.group(4);

                                    // Process the schema
                                    JsonValue schema = JsonValueFactory.create(resource.getInputStream());
                                    permissionSchemas = permissionSchemas.put(
                                            JsonArray.of(urlPath, method, permissionName, requestOrResponse), schema);

                                    String permissionConfKey = permissionConfKeyOf(method, requestOrResponse, permissionName);
                                    List<PathPatternPermissionConfig> list = permissionConfByMethodAndPathPattern.get(permissionConfKey);
                                    list = list == null ? new ArrayList<>() : list;
                                    list.add(new PathPatternPermissionConfig(urlPath, schema.getJsonObject()));

                                    permissionConfByMethodAndPathPattern.put(permissionConfKey, list);
                                }
                            }
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    });
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }

    // Helper method to normalize resource paths and handle JAR-based resources
    private String normalizeResourcePath(Resource resource) throws IOException {
        try {
            URI uri = resource.getURI();
            if ("jar".equals(uri.getScheme())) {
                // Handle JAR-based resources
                String fullPath = uri.toString();
                // Example: jar:file:/path/to/app.jar!/BASE_DIR
                int delimiterIndex = fullPath.indexOf("!/");
                if (delimiterIndex > 0) {
                    return fullPath.substring(delimiterIndex + 2); // Extract internal JAR path
                } else {
                    return null;
                }
            } else {
                // Handle file-based resources
                return resource.getFile().getCanonicalPath();
            }
        } catch (IOException | IllegalStateException e) {
            return null; // Resource cannot be resolved
        }
    }
}