package com.zuunr.api.openapi;

import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class JsonUriTest {

    @Test
    void test1() {
        JsonUri jsonUri = JsonValue.of("abc").as(JsonUri.class);
        assertThat(jsonUri.getPath(), is(JsonValue.of("abc")));
        assertThat(jsonUri.getPort(), is(JsonValue.of(-1)));
        assertThat(jsonUri.getScheme(), is(JsonValue.NULL));
        assertThat(jsonUri.getHost(), is(JsonValue.NULL));
        assertThat(jsonUri.getQuery(), is(JsonValue.NULL));
    }

    @Test
    void test_url() {
        JsonUri jsonUri = JsonValue.of("https://abc.com/my/path").as(JsonUri.class);
        assertThat(jsonUri.getPath(), is(JsonValue.of("/my/path")));
        assertThat(jsonUri.getPort(), is(JsonValue.of(-1)));
        assertThat(jsonUri.getScheme(), is(JsonValue.of("https")));
        assertThat(jsonUri.getHost(), is(JsonValue.of("abc.com")));
        assertThat(jsonUri.getQuery(), is(JsonValue.NULL));
    }

    @Test
    void test_string_with_slashes() {
        JsonValue path = JsonValue.of("abc/def/ghi").as(JsonUri.class).getPath();
        assertThat(path, is(JsonValue.of("abc/def/ghi")));
    }

    @Test
    void test_string_with_query() {
        JsonValue path = JsonValue.of("abc/?def/ghi").as(JsonUri.class).getPath();
        assertThat(path, is(JsonValue.of("abc/")));
    }

    @Test
    void test_url_without_path() {
        JsonValue path = JsonValue.of("https://abc.com").as(JsonUri.class).getPath();
        assertThat(path, is(JsonValue.of("")));
    }

    @Test
    void test_url_without_path_with_query() {
        JsonUri jsonUri = JsonValue.of("https://abc.com?name=peter").as(JsonUri.class);
        assertThat(jsonUri.getPath(), is(JsonValue.of("")));
        assertThat(jsonUri.getPort(), is(JsonValue.of(-1)));
        assertThat(jsonUri.getScheme(), is(JsonValue.of("https")));
        assertThat(jsonUri.getHost(), is(JsonValue.of("abc.com")));
        assertThat(jsonUri.getQuery(), is(JsonValue.of("name=peter")));
    }
}
